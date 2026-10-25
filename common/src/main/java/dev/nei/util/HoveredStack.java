package dev.nei.util;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import net.minecraft.client.MinecraftClient;

/** The stack currently under the mouse, whether it is in a slot, EMI's index or the NEI panel. */
public final class HoveredStack {
	private HoveredStack() {
	}

	/** Unwraps favorites/recipe favorites into the stack they stand for. */
	private static EmiStack resolve(dev.emi.emi.api.stack.EmiStackInteraction interaction) {
		if (interaction == null) {
			return EmiStack.EMPTY;
		}
		try {
			EmiIngredient ingredient = interaction.getStack();
			if (ingredient == null) {
				return EmiStack.EMPTY;
			}
			if (ingredient instanceof EmiStack stack) {
				return stack;
			}
			var context = EmiApi.getRecipeContext(ingredient);
			if (context != null && context.getOutputs() != null) {
				for (EmiStack output : context.getOutputs()) {
					if (output != null && !output.isEmpty()) {
						return output;
					}
				}
			}
			for (EmiStack stack : ingredient.getEmiStacks()) {
				if (stack != null && !stack.isEmpty()) {
					return stack;
				}
			}
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not resolve hovered ingredient", t);
		}
		return EmiStack.EMPTY;
	}

	public static EmiStack get() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen == null) {
			return EmiStack.EMPTY;
		}
		// EMI first: its index, favorites, search results and container slots are the authority.
		// EMI's own hover state is what its keybinds use; it knows about its index, favorites and
		// recipe favorites. The cursor-position lookup is a fallback for the first frame after a
		// screen opens, before EMI has seen a mouse move.
		var cached = EmiApi.getHoveredStack(true);
		EmiStack resolved = resolve(cached);
		if (!resolved.isEmpty()) {
			return resolved;
		}
		try {
			// Mouse coordinates are raw window pixels; EMI works in GUI-scaled units.
			double scale = Math.max(1.0, client.getWindow().getScaleFactor());
			int mouseX = (int) (client.mouse.getX() / scale);
			int mouseY = (int) (client.mouse.getY() / scale);
			var atCursor = EmiApi.getHoveredStack(mouseX, mouseY, true);
			resolved = resolve(atCursor);
			if (!resolved.isEmpty()) {
				return resolved;
			}
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] cursor hover lookup failed", t);
		}

		return EmiStack.EMPTY;
	}
}