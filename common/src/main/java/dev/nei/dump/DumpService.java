package dev.nei.dump;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonElement;

import dev.emi.emi.EmiRecipes;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.NeiClient;
import dev.nei.util.StackIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Writes searchable text dumps of the EMI index into {@code config/nei-dump/}.
 *
 * <p>All work happens on a daemon thread so a big pack never freezes the client. Files are written
 * to a {@code .tmp} sibling first and then moved into place, so a crash mid-write never leaves a
 * half-written dump behind. Call {@link #dumpAll()} / {@link #dumpItems()} /
 * {@link #dumpRecipes()} / {@link #dumpTags()} from a key bind; progress is reported in chat.
 */
public final class DumpService {
	private static final String DIRECTORY = "nei-dump";
	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private DumpService() {
	}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	public static void dumpAll() {
		run("all");
	}

	public static void dumpItems() {
		run("items");
	}

	public static void dumpRecipes() {
		run("recipes");
	}

	public static void dumpTags() {
		run("tags");
	}

	private static void run(String kind) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		if (!NeiClient.isInitialised()) {
			Nei.LOG.warn("[MaEMI] dump requested before the client was initialised");
			return;
		}
		if (!RUNNING.compareAndSet(false, true)) {
			chat(client, "nei.dump.busy");
			return;
		}
		Path directory;
		try {
			directory = NeiClient.platform().configDirectory().resolve(DIRECTORY);
		} catch (Throwable t) {
			RUNNING.set(false);
			Nei.LOG.error("[MaEMI] could not resolve the dump directory", t);
			return;
		}
		chat(client, "nei.dump.started");
		Thread thread = new Thread(() -> {
			try {
				Files.createDirectories(directory);
				if (kind.equals("all") || kind.equals("items")) {
					dumpItemsTo(directory.resolve("items.txt"));
				}
				if (kind.equals("all") || kind.equals("recipes")) {
					dumpRecipesTo(directory.resolve("recipes.txt"));
				}
				if (kind.equals("all") || kind.equals("tags")) {
					dumpTagsTo(directory.resolve("tags.txt"));
				}
				chat(client, "nei.dump.done", directory.toAbsolutePath().toString());
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] dump failed", t);
				chat(client, "nei.dump.failed", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
			} finally {
				RUNNING.set(false);
			}
		}, "MaEMI-Dump");
		thread.setDaemon(true);
		thread.start();
	}

	private static void dumpItemsTo(Path target) throws IOException {
		writeAtomic(target, writer -> {
			writer.write("# MaEMI item dump: id, count, nbt, serialized stack");
			writer.newLine();
			List<EmiStack> stacks = new ArrayList<>(EmiApi.getIndexStacks());
			stacks.sort((a, b) -> {
				Identifier first = a == null ? null : a.getId();
				Identifier second = b == null ? null : b.getId();
				return String.valueOf(first).compareTo(String.valueOf(second));
			});
			for (EmiStack stack : stacks) {
				if (stack == null || stack.isEmpty() || stack.getId() == null) {
					continue;
				}
				String nbt = stack.getNbt() == null ? "-" : stack.getNbt().asString();
				JsonElement serialized = StackIO.write(stack);
				writer.write(stack.getId() + "\t" + stack.getAmount() + "\t" + nbt + "\t"
						+ (serialized == null ? "-" : serialized.toString()));
				writer.newLine();
			}
		});
	}

	private static void dumpRecipesTo(Path target) throws IOException {
		writeAtomic(target, writer -> {
			writer.write("# MaEMI recipe dump: recipe id, category, inputs, outputs");
			writer.newLine();
			List<EmiRecipe> recipes = EmiRecipes.recipes == null ? List.of() : EmiRecipes.recipes;
			for (EmiRecipe recipe : recipes) {
				if (recipe == null) {
					continue;
				}
				String id = recipe.getId() == null ? "-" : recipe.getId().toString();
				String category = recipe.getCategory() == null || recipe.getCategory().getId() == null
						? "-" : recipe.getCategory().getId().toString();
				writer.write(id + "\t" + category + "\t" + describeIngredients(recipe.getInputs()) + "\t"
						+ describeStacks(recipe.getOutputs()));
				writer.newLine();
			}
		});
	}

	private static void dumpTagsTo(Path target) throws IOException {
		writeAtomic(target, writer -> {
			writer.write("# MaEMI tag dump: item id, item tags");
			writer.newLine();
			List<String> rows = new ArrayList<>();
			for (Item item : Registry.ITEM) {
				if (item == null) {
					continue;
				}
				ItemStack stack = item.getDefaultStack();
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				Identifier id = Registry.ITEM.getId(item);
				List<String> tags;
				try {
					tags = stack.streamTags().map(tag -> tag.id().toString()).sorted().toList();
				} catch (Throwable t) {
					continue;
				}
				if (tags.isEmpty()) {
					continue;
				}
				rows.add(id + "\t" + String.join(", ", tags));
			}
			rows.sort(String::compareTo);
			for (String row : rows) {
				writer.write(row);
				writer.newLine();
			}
		});
	}

	private static String describeIngredients(List<EmiIngredient> ingredients) {
		if (ingredients == null || ingredients.isEmpty()) {
			return "-";
		}
		List<String> parts = new ArrayList<>();
		for (EmiIngredient ingredient : ingredients) {
			if (ingredient == null || ingredient.isEmpty()) {
				continue;
			}
			parts.add(describeStacks(ingredient.getEmiStacks()) + "x" + ingredient.getAmount());
		}
		return parts.isEmpty() ? "-" : String.join(" + ", parts);
	}

	private static String describeStacks(List<EmiStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return "-";
		}
		List<String> parts = new ArrayList<>();
		for (EmiStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			parts.add(String.valueOf(stack.getId()) + "x" + stack.getAmount());
		}
		return parts.isEmpty() ? "-" : String.join(" / ", parts);
	}

	private static void writeAtomic(Path target, IoWriter body) throws IOException {
		Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
		try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			body.write(writer);
		} catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
				// best effort
			}
			throw e;
		}
		try {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void chat(MinecraftClient client, String key, Object... args) {
		try {
			client.execute(() -> {
				ClientPlayerEntity player = client.player;
				if (player != null) {
					player.sendMessage(new net.minecraft.text.TranslatableText(key, args), false);
				}
			});
		} catch (Throwable t) {
			Nei.LOG.warn("[MaEMI] could not report dump progress", t);
		}
	}

	@FunctionalInterface
	private interface IoWriter {
		void write(BufferedWriter writer) throws IOException;
	}
}
