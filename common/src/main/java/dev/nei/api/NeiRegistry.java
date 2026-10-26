package dev.nei.api;

import java.util.function.Predicate;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.screen.ScreenHandlerType;

/**
 * A small, stable façade over EMI's registry. Plugins get this instead of {@link EmiRegistry} so
 * that MaEMI can keep track of what was added and so that addons compiled against a slightly older
 * EMI still work.
 */
public final class NeiRegistry {
	private final EmiRegistry emi;

	public NeiRegistry(EmiRegistry emi) {
		this.emi = emi;
	}

	public EmiRegistry emi() {
		return emi;
	}

	public void addCategory(EmiRecipeCategory category) {
		emi.addCategory(category);
	}

	public void addRecipe(EmiRecipe recipe) {
		emi.addRecipe(recipe);
	}

	public void addWorkstation(EmiRecipeCategory category, EmiIngredient workstation) {
		emi.addWorkstation(category, workstation);
	}

	public <T extends net.minecraft.screen.ScreenHandler> void addRecipeHandler(ScreenHandlerType<T> type, EmiRecipeHandler<T> handler) {
		emi.addRecipeHandler(type, handler);
	}

	public void removeRecipes(Predicate<EmiRecipe> predicate) {
		emi.removeRecipes(predicate);
	}

	public void addEmiStack(EmiStack stack) {
		emi.addEmiStack(stack);
	}

	public void addAlias(EmiIngredient stack, net.minecraft.text.Text text) {
		try {
			EmiRegistry.class.getMethod("addAlias", EmiIngredient.class, net.minecraft.text.Text.class)
					.invoke(emi, stack, text);
		} catch (Throwable ignored) {
		}
	}
}