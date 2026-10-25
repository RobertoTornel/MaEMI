package dev.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

/**
 * NEI's view of EMI's recipe index. NEI used to own a handler registry; on 1.20.1 EMI is the index,
 * so this class provides the NEI-shaped queries (recipes for a stack, uses of a stack, recipe
 * categories for a workstation) and the plugin bootstrap.
 */
public final class RecipeRegistry {
	private static boolean initialised;

	private RecipeRegistry() {
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
				Nei.LOG.error("[NEI] plugin {} failed to register", plugin.modId(), t);
			}
		}
		Nei.LOG.info("[NEI] recipe registry ready ({} plugin(s))", plugins);
	}

	public static boolean isInitialised() {
		return initialised;
	}

	public static List<EmiRecipe> recipesFor(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			List<EmiRecipe> recipes = EmiApi.getRecipeManager().getRecipesByOutput(stack);
			if (recipes == null) {
				Nei.LOG.warn("[NEI] EMI returned a null recipe list for {}", stack);
				return Collections.emptyList();
			}
			return recipes;
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] recipe lookup failed for {}", stack, t);
			return Collections.emptyList();
		}
	}

	public static List<EmiRecipe> usesFor(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return EmiApi.getRecipeManager().getRecipesByInput(stack);
		} catch (Throwable t) {
			return Collections.emptyList();
		}
	}

	public static List<EmiRecipe> byCategory(EmiRecipeCategory category) {
		try {
			return EmiApi.getRecipeManager().getRecipes(category);
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
			for (EmiRecipeCategory category : EmiApi.getRecipeManager().getCategories()) {
				for (EmiIngredient station : EmiApi.getRecipeManager().getWorkstations(category)) {
					for (EmiStack candidate : station.getEmiStacks()) {
						if (candidate.isEqual(stack)) {
							result.add(category);
							break;
						}
					}
				}
			}
		} catch (Throwable t) {
			Nei.LOG.debug("[NEI] workstation lookup failed", t);
		}
		return result;
	}
}
