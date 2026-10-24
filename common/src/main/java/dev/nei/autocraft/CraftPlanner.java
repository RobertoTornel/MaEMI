package dev.nei.autocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.recipe.RecipeRegistry;
import dev.nei.util.StackIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Works out how to obtain a stack from what the player is carrying, using EMI's recipe index.
 * Deliberately conservative: it never assumes a recipe can be crafted without the inputs, it stops
 * at cycles, and it reports what is missing instead of producing a plan that cannot run.
 */
public final class CraftPlanner {
	private static final int MAX_DEPTH = 12;
	private static final int MAX_STEPS = 256;

	private CraftPlanner() {
	}

	public static CraftPlan plan(EmiStack target, int amount) {
		List<CraftStep> steps = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		if (target == null || target.isEmpty()) {
			return new CraftPlan(EmiStack.EMPTY, amount, steps, List.of("nothing selected"));
		}
		Map<String, Long> available = countInventory();
		Map<String, Long> produced = new HashMap<>();
		plan(target, Math.max(1, amount), 0, available, produced, steps, missing, new HashSet<>());
		return new CraftPlan(target, amount, steps, missing);
	}

	private static long plan(EmiStack stack, long needed, int depth, Map<String, Long> available,
			Map<String, Long> produced, List<CraftStep> steps, List<String> missing, Set<String> path) {
		String key = StackIO.key(stack);
		long have = available.getOrDefault(key, 0L) + produced.getOrDefault(key, 0L);
		long shortfall = needed - have;
		if (shortfall <= 0) {
			return have;
		}
		if (depth > MAX_DEPTH || steps.size() > MAX_STEPS || !path.add(key)) {
			missing.add(key);
			return have;
		}
		EmiRecipe recipe = chooseRecipe(stack, path);
		if (recipe == null) {
			missing.add(key);
			path.remove(key);
			return have;
		}
		long perCraft = producedAmount(recipe, stack);
		long batches = Math.max(1, (shortfall + perCraft - 1) / perCraft);
		for (EmiIngredient input : recipe.getInputs()) {
			EmiStack chosen = chooseInput(input);
			if (chosen.isEmpty()) {
				continue;
			}
			long inputAmount = Math.max(1, input.getAmount()) * batches;
			plan(chosen, inputAmount, depth + 1, available, produced, steps, missing, path);
		}
		long total = produced.getOrDefault(key, 0L) + batches * perCraft;
		produced.put(key, total);
		steps.add(new CraftStep(recipe, (int) Math.min(batches, Integer.MAX_VALUE)));
		path.remove(key);
		return total;
	}

	private static EmiRecipe chooseRecipe(EmiStack stack, Set<String> path) {
		EmiRecipe fallback = null;
		for (EmiRecipe recipe : RecipeRegistry.recipesFor(stack)) {
			if (recipe.getOutputs().isEmpty() || producedAmount(recipe, stack) <= 0) {
				continue;
			}
			if (fallback == null) {
				fallback = recipe;
			}
			if (isVanillaCrafting(recipe)) {
				return recipe;
			}
		}
		return fallback;
	}

	private static boolean isVanillaCrafting(EmiRecipe recipe) {
		try {
			return recipe.getCategory() != null && recipe.getCategory().getId() != null
				&& "minecraft:crafting".equals(recipe.getCategory().getId().toString());
		} catch (Throwable t) {
			return false;
		}
	}

	private static long producedAmount(EmiRecipe recipe, EmiStack stack) {
		long amount = 0;
		for (EmiStack output : recipe.getOutputs()) {
			if (output.isEqual(stack)) {
				amount += Math.max(1, output.getAmount());
			}
		}
		return amount;
	}

	private static EmiStack chooseInput(EmiIngredient input) {
		EmiStack best = EmiStack.EMPTY;
		long bestCount = -1;
		for (EmiStack candidate : input.getEmiStacks()) {
			if (candidate.isEmpty()) {
				continue;
			}
			long count = countInInventory(candidate);
			if (count > bestCount) {
				best = candidate;
				bestCount = count;
			}
		}
		return best;
	}

	private static Map<String, Long> countInventory() {
		Map<String, Long> counts = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return counts;
		}
		PlayerInventory inventory = client.player.getInventory();
		for (ItemStack stack : inventory.main) {
			add(counts, stack);
		}
		for (ItemStack stack : inventory.offHand) {
			add(counts, stack);
		}
		return counts;
	}

	private static void add(Map<String, Long> counts, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String key = StackIO.key(EmiStack.of(stack));
		counts.merge(key, (long) stack.getCount(), Long::sum);
	}

	private static long countInInventory(EmiStack stack) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return 0;
		}
		long total = 0;
		PlayerInventory inventory = client.player.getInventory();
		for (ItemStack candidate : inventory.main) {
			if (!candidate.isEmpty() && EmiStack.of(candidate).isEqual(stack)) {
				total += candidate.getCount();
			}
		}
		for (ItemStack candidate : inventory.offHand) {
			if (!candidate.isEmpty() && EmiStack.of(candidate).isEqual(stack)) {
				total += candidate.getCount();
			}
		}
		return total;
	}
}
