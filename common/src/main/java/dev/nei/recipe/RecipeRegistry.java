package dev.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.emi.emi.EmiRecipes;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.api.NeiPlugin;
import dev.nei.api.NeiPlugins;
import dev.nei.api.NeiRegistry;
import dev.nei.util.ToolIdentity;
import net.minecraft.util.Identifier;

/**
 * MaEMI's view of EMI's recipe index. MaEMI used to own a handler registry; on 1.20.1 EMI is the index,
 * so this class provides the MaEMI-shaped queries (recipes for a stack, uses of a stack, recipe
 * categories for a workstation) and the plugin bootstrap.
 */
public final class RecipeRegistry {
	private static boolean initialised;
	// GT tools carry tool NBT, so EMI's NBT-sensitive output lookup misses their crafting recipes.
	// This index is limited to tool outputs and keyed by item id, so material variants of plates
	// and ingots can never bleed into each other.
	private static List<EmiRecipe> toolIndexSource;
	private static Map<Identifier, List<EmiRecipe>> toolOutputIndex = Collections.emptyMap();

	private RecipeRegistry() {
	}

	private static Map<Identifier, List<EmiRecipe>> toolOutputIndex() {
		List<EmiRecipe> all = allRecipes();
		if (all == toolIndexSource) {
			return toolOutputIndex;
		}
		Map<Identifier, List<EmiRecipe>> index = new HashMap<>();
		for (EmiRecipe recipe : all) {
			if (recipe == null || recipe.getOutputs() == null) {
				continue;
			}
			for (EmiStack output : recipe.getOutputs()) {
				if (output == null || output.isEmpty() || !ToolIdentity.isTool(output)) {
					continue;
				}
				Identifier id = output.getId();
				if (id != null) {
					index.computeIfAbsent(id, k -> new ArrayList<>()).add(recipe);
				}
			}
		}
		for (Map.Entry<Identifier, List<EmiRecipe>> entry : index.entrySet()) {
			entry.setValue(List.copyOf(entry.getValue()));
		}
		toolIndexSource = all;
		toolOutputIndex = index;
		Nei.LOG.info("[MaEMI] built tool-output recipe index ({} tool items)", index.size());
		return index;
	}

	public static void init(EmiRegistry registry) {
		initialised = true;
		NeiRegistry facade = new NeiRegistry(registry);
		int plugins = 0;
		for (NeiPlugin plugin : NeiPlugins.all()) {
			try {
				plugin.register(facade);
				plugins++;
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] plugin {} failed to register", plugin.modId(), t);
			}
		}
		Nei.LOG.info("[MaEMI] recipe registry ready ({} plugin(s))", plugins);
	}

	public static boolean isInitialised() {
		return initialised;
	}

	public static List<EmiRecipe> recipesFor(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			List<EmiRecipe> recipes = lookup(EmiRecipes.byOutput, stack, true);
			if (!recipes.isEmpty() || !ToolIdentity.isTool(stack)) {
				return recipes;
			}
			Identifier id = stack.getId();
			return id == null ? Collections.emptyList()
				: toolOutputIndex().getOrDefault(id, Collections.emptyList());
		} catch (Throwable t) {
			Nei.LOG.warn("[MaEMI] recipe lookup failed for {}", stack, t);
			return Collections.emptyList();
		}
	}


	public static List<EmiRecipe> usesFor(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return lookup(EmiRecipes.byInput, stack, false);
		} catch (Throwable t) {
			return Collections.emptyList();
		}
	}

	public static List<EmiRecipe> byCategory(EmiRecipeCategory category) {
		try {
			if (category == null || EmiRecipes.byCategory == null) {
				return Collections.emptyList();
			}
			return EmiRecipes.byCategory.getOrDefault(category, Collections.emptyList());
		} catch (Throwable t) {
			return Collections.emptyList();
		}
	}

	public static boolean hasRecipes(EmiStack stack) {
		return !recipesFor(stack).isEmpty();
	}

	public static boolean hasUses(EmiStack stack) {
		return !usesFor(stack).isEmpty();
	}

	public static void displayRecipes(EmiStack stack) {
		if (stack != null && !stack.isEmpty()) {
			EmiApi.displayRecipes(stack);
		}
	}

	public static void displayUses(EmiStack stack) {
		if (stack != null && !stack.isEmpty()) {
			EmiApi.displayUses(stack);
		}
	}

	public static void displayCategory(EmiRecipeCategory category) {
		if (category != null) {
			EmiApi.displayRecipeCategory(category);
		}
	}

	/** Categories whose workstation list contains this stack, i.e. "what can this machine make". */
	public static List<EmiRecipeCategory> categoriesForWorkstation(EmiStack stack) {
		List<EmiRecipeCategory> result = new ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return result;
		}
		try {
			List<EmiRecipeCategory> categories = EmiRecipes.categories;
			if (categories == null || EmiRecipes.workstations == null) {
				return result;
			}
			for (EmiRecipeCategory category : categories) {
				List<EmiIngredient> stations = EmiRecipes.workstations.get(category);
				if (stations == null) {
					continue;
				}
				for (EmiIngredient station : stations) {
					for (EmiStack candidate : station.getEmiStacks()) {
						if (candidate.isEqual(stack)) {
							result.add(category);
							break;
						}
					}
				}
			}
		} catch (Throwable t) {
			Nei.LOG.debug("[MaEMI] workstation lookup failed", t);
		}
		return result;
	}

	private static List<EmiRecipe> allRecipes() {
		List<EmiRecipe> all = EmiRecipes.recipes;
		return all == null ? Collections.emptyList() : all;
	}

	/** 0.7.x keeps the same lookup maps EMI uses internally, keyed by {@link EmiStack#getKey()}. */
	private static List<EmiRecipe> lookup(Map<Object, List<EmiRecipe>> index, EmiStack stack, boolean outputs) {
		if (index == null) {
			return Collections.emptyList();
		}
		Object key = stack.getKey();
		if (key == null) {
			return Collections.emptyList();
		}
		List<EmiRecipe> candidates = index.get(key);
		if (candidates == null || candidates.isEmpty()) {
			return Collections.emptyList();
		}
		List<EmiRecipe> matches = new ArrayList<>();
		for (EmiRecipe recipe : candidates) {
			if (recipe == null) {
				continue;
			}
			if (outputs ? hasOutput(recipe, stack) : hasInput(recipe, stack)) {
				matches.add(recipe);
			}
		}
		return matches;
	}

	private static boolean hasOutput(EmiRecipe recipe, EmiStack stack) {
		for (EmiStack output : recipe.getOutputs()) {
			if (output != null && output.isEqual(stack)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasInput(EmiRecipe recipe, EmiStack stack) {
		for (EmiIngredient input : recipe.getInputs()) {
			for (EmiStack candidate : input.getEmiStacks()) {
				if (candidate.isEqual(stack)) {
					return true;
				}
			}
		}
		for (EmiIngredient catalyst : recipe.getCatalysts()) {
			for (EmiStack candidate : catalyst.getEmiStacks()) {
				if (candidate.isEqual(stack)) {
					return true;
				}
			}
		}
		return false;
	}
}