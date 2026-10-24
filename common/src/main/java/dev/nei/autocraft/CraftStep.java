package dev.nei.autocraft;

import dev.emi.emi.api.recipe.EmiRecipe;

/** One recipe to run, with the number of times it has to be crafted. */
public record CraftStep(EmiRecipe recipe, int batches) {
}
