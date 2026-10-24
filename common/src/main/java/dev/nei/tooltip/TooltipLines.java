package dev.nei.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.nei.NeiClient;
import dev.nei.config.NeiConfig;
import dev.nei.config.NeiConfigData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Builds the extra lines NEI appends to an item's tooltip.
 *
 * <p>The loader is deliberately not referenced here: mod display names are looked up in a small map
 * that the platform layer fills with {@link #registerModName(String, String)} (and
 * {@link #registerModNames(Map)}). Until it does, the namespace is humanised
 * ({@code create} becomes "Create", {@code minecraft} becomes "Minecraft").
 */
public final class TooltipLines {
	private static final int MAX_TAGS = 20;
	private static final int MAX_NBT_LINES = 8;
	private static final int MAX_TAG_LENGTH = 120;
	private static final int MAX_NBT_VALUE_LENGTH = 160;
	private static final Map<String, String> MOD_NAMES = new ConcurrentHashMap<>();

	private TooltipLines() {
	}

	/** Filled by the loader module; a missing entry simply falls back to the namespace. */
	public static void registerModName(String namespace, String displayName) {
		if (namespace != null && !namespace.isBlank() && displayName != null && !displayName.isBlank()) {
			MOD_NAMES.put(namespace, displayName);
		}
	}

	public static void registerModNames(Map<String, String> names) {
		if (names == null) {
			return;
		}
		for (Map.Entry<String, String> entry : names.entrySet()) {
			registerModName(entry.getKey(), entry.getValue());
		}
	}

	public static String modName(Identifier id) {
		return id == null ? "" : modName(id.getNamespace());
	}

	public static String modName(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			return "Minecraft";
		}
		String known = MOD_NAMES.get(namespace);
		if (known != null) {
			return known;
		}
		try {
			if (NeiClient.isInitialised()) {
				String loaderName = NeiClient.platform().modNameFor(namespace);
				if (loaderName != null && !loaderName.isBlank() && !loaderName.equals(namespace)) {
					return loaderName;
				}
			}
		} catch (Throwable ignored) {
			// fall through to the humanised namespace
		}
		if (namespace.equals("minecraft")) {
			return "Minecraft";
		}
		String[] parts = namespace.replace('-', '_').split("_");
		StringBuilder builder = new StringBuilder(namespace.length());
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				builder.append(part.substring(1));
			}
		}
		return builder.length() == 0 ? namespace : builder.toString();
	}

	/** The lines to append, honouring the tooltip section of the config. Never null. */
	public static List<Text> build(ItemStack stack) {
		List<Text> lines = new ArrayList<>();
		append(stack, lines);
		return lines;
	}

	/** Appends NEI's lines to {@code tooltip}. Safe with a null/empty stack or an immutable list. */
	public static void append(ItemStack stack, List<Text> tooltip) {
		if (tooltip == null || stack == null || stack.isEmpty()) {
			return;
		}
		NeiConfigData.Tooltip cfg = NeiConfig.get().tooltip;
		NeiConfigData.General general = NeiConfig.get().general;
		if (cfg == null || !cfg.enabled || (general != null && !general.enabled)) {
			return;
		}
		List<Text> extra = new ArrayList<>();
		Identifier id = Registries.ITEM.getId(stack.getItem());
		if (id != null && cfg.showRegistryName) {
			extra.add(Text.translatable("nei.tooltip.id", id.toString()));
		}
		if (id != null && cfg.showModName) {
			extra.add(Text.translatable("nei.tooltip.mod", modName(id)));
		}
		if (cfg.showItemCount) {
			extra.add(Text.translatable("nei.tooltip.stack_size", stack.getCount(), stack.getMaxCount()));
		}
		if (cfg.showTags) {
			appendTags(stack, extra);
		}
		if (cfg.showNbt) {
			appendNbt(stack, extra);
		}
		try {
			tooltip.addAll(extra);
		} catch (UnsupportedOperationException e) {
			// Another mod handed us an immutable list; nothing sensible we can do.
		}
	}

	private static void appendTags(ItemStack stack, List<Text> extra) {
		List<String> tags;
		try {
			tags = stack.streamTags().map(tag -> tag.id().toString()).sorted().toList();
		} catch (Throwable t) {
			return;
		}
		if (tags.isEmpty()) {
			return;
		}
		int shown = Math.min(MAX_TAGS, tags.size());
		String joined = String.join(", ", tags.subList(0, shown));
		if (joined.length() > MAX_TAG_LENGTH) {
			joined = joined.substring(0, MAX_TAG_LENGTH - 1) + "\u2026";
		}
		extra.add(Text.translatable("nei.tooltip.tags", joined));
		if (tags.size() > shown) {
			extra.add(Text.translatable("nei.tooltip.tags_more", tags.size() - shown));
		}
	}

	private static void appendNbt(ItemStack stack, List<Text> extra) {
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || nbt.isEmpty()) {
			return;
		}
		List<String> keys = new ArrayList<>(nbt.getKeys());
		Collections.sort(keys);
		int shown = Math.min(MAX_NBT_LINES, keys.size());
		for (int i = 0; i < shown; i++) {
			String key = keys.get(i);
			NbtElement element = nbt.get(key);
			String value = element == null ? "?" : element.asString();
			if (value.length() > MAX_NBT_VALUE_LENGTH) {
				value = value.substring(0, MAX_NBT_VALUE_LENGTH - 1) + "\u2026";
			}
			String line = key + ": " + value;
			if (i == 0) {
				extra.add(Text.translatable("nei.tooltip.nbt", line));
			} else {
				extra.add(Text.literal("  " + line));
			}
		}
		if (keys.size() > shown) {
			extra.add(Text.translatable("nei.tooltip.nbt_more", keys.size() - shown));
		}
	}
}