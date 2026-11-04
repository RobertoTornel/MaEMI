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
import dev.nei.util.ToolIdentity;
import net.minecraft.util.Identifier;

/**
 * MaEMI's view of EMI's recipe index. MaEMI used to own a handler registry; on 1.20.1 EMI is the index,
 * so this class provides the MaEMI-shaped queries (recipes for a stack, uses of a stack, recipe
 * categories for a workstation) and the plugin bootstrap.
 */
public final class RecipeRegistry {
	private static boolean initialised;
	// EMI's output index is NBT-sensitive, so a stack that carries no NBT finds nothing when every
	// recipe for that item produces a tagged one. Sophisticated Storage barrels are the clearest
	// case: a pristine barrel matched no recipe at all, while the same barrel with
	// {woodType: spruce} matched three, and the planner reported the barrel as missing. GT tools
	// have the same problem with their tool NBT.
	//
	// This fallback index is keyed by item id only. That is safe because distinct GregTech
	// materials are distinct items (tin_plate is not iron_plate); the ids differ, so no material
	// can bleed into another. NBT variants of one item are exactly what we want to unify.
	private static List<EmiRecipe> idIndexSource;
	private static Map<Identifier, List<EmiRecipe>> idOutputIndex = Collections.emptyMap();

	private RecipeRegistry() {
	}

	private static Map<Identifier, List<EmiRecipe>> idOutputIndex() {
		List<EmiRecipe> all = EmiApi.getRecipeManager().getRecipes();
		if (all == idIndexSource) {
			return idOutputIndex;
		}
		long startedAt = System.nanoTime();
		Map<Identifier, List<EmiRecipe>> index = new HashMap<>();
		for (EmiRecipe recipe : all) {
			if (recipe == null || recipe.getOutputs() == null) {
				continue;
			}
			for (EmiStack output : recipe.getOutputs()) {
				if (output == null || output.isEmpty()) {
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
		idIndexSource = all;
		idOutputIndex = index;
		Nei.LOG.info("[MaEMI] built id-keyed recipe index ({} items) in {} ms", index.size(),
			(System.nanoTime() - startedAt) / 1_000_000);
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
			List<EmiRecipe> recipes = EmiApi.getRecipeManager().getRecipesByOutput(stack);
			if (recipes != null && !recipes.isEmpty()) {
				return recipes;
			}
			// EMI compares NBT in this lookup, so a stack carrying none finds nothing when every
			// recipe for that item produces a tagged variant. A pristine Sophisticated Storage
			// barrel matched zero recipes while the same barrel with {woodType: spruce} matched
			// three, and the planner called the barrel missing. Fall back to the id-keyed index:
			// a recipe that makes this item with some NBT is still a recipe for this item.
			Identifier id = stack.getId();
			if (id == null) {
				return recipes == null ? Collections.emptyList() : recipes;
			}
			List<EmiRecipe> byId = idOutputIndex().getOrDefault(id, Collections.emptyList());
			return byId.isEmpty() ? (recipes == null ? Collections.emptyList() : recipes) : byId;
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
			Nei.LOG.debug("[MaEMI] workstation lookup failed", t);
		}
		return result;
	}
}