package dev.nei.autocraft;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

/** An ordered set of crafting steps (inputs before the recipes that consume them). */
public record CraftPlan(EmiStack target, int requested, List<CraftStep> steps, List<String> missing) {
	public boolean isEmpty() {
		return steps.isEmpty();
	}

	public boolean complete() {
		return missing.isEmpty();
	}
}
