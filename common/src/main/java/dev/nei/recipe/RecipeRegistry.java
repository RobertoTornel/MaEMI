package dev.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * NEI's view of EMI's recipe index. NEI used to own a handler registry; on 1.20.1 EMI is the index,
 * so this class provides the NEI-shaped queries (recipes for a stack, uses of a stack, recipe
 * categories for a workstation) and the plugin bootstrap.
 */
public final class RecipeRegistry {
	private static boolean initialised;
	// Fallback output index by item, for stacks whose NBT (tool stats, damage) does not match the
	// recipe's output stack exactly. EMI's own map is NBT-sensitive for those items.
	private static List<EmiRecipe> itemIndexSource;
	private static Map<Item, List<EmiRecipe>> itemOutputIndex = Collections.emptyMap();

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
			if (recipes != null && !recipes.isEmpty()) {
				return recipes;
			}
			ItemStack itemStack = stack.getItemStack();
			if (itemStack == null || itemStack.isEmpty()) {
				return Collections.emptyList();
			}
			List<EmiRecipe> byItem = itemOutputIndex().get(itemStack.getItem());
			return byItem == null ? Collections.emptyList() : byItem;
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] recipe lookup failed for {}", stack, t);
			return Collections.emptyList();
		}
	}

	private static Map<Item, List<EmiRecipe>> itemOutputIndex() {
		List<EmiRecipe> all = EmiApi.getRecipeManager().getRecipes();
		if (all == itemIndexSource) {
			return itemOutputIndex;
		}
		Map<Item, List<EmiRecipe>> index = new HashMap<>();
		for (EmiRecipe recipe : all) {
			if (recipe == null || recipe.getOutputs() == null) {
				continue;
			}
			for (EmiStack output : recipe.getOutputs()) {
				if (output == null || output.isEmpty()) {
					continue;
				}
				ItemStack outputStack = output.getItemStack();
				if (outputStack == null || outputStack.isEmpty()) {
					continue;
				}
				index.computeIfAbsent(outputStack.getItem(), k -> new ArrayList<>()).add(recipe);
			}
		}
		for (Map.Entry<Item, List<EmiRecipe>> entry : index.entrySet()) {
			entry.setValue(List.copyOf(entry.getValue()));
		}
		itemIndexSource = all;
		itemOutputIndex = index;
		Nei.LOG.info("[NEI] built item-output recipe fallback ({} items, {} recipes)", index.size(), all.size());
		return index;
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