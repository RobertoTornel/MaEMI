package dev.nei.autocraft;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

/**
 * An ordered set of crafting steps (inputs before the recipes that consume them).
 *
 * <p>{@code timedOut} marks a plan that ran into the exploration budget before it could decide:
 * the {@code missing} list is then a guess rather than a real answer, and the caller says so
 * instead of claiming the item cannot be crafted.</p>
 */
public record CraftPlan(EmiStack target, int requested, List<CraftStep> steps, List<String> missing,
		boolean timedOut, List<String> missingTools) {
	public boolean isEmpty() {
		return steps.isEmpty();
	}

	public boolean complete() {
		return missing.isEmpty();
	}
}
