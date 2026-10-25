package dev.nei.util;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;

/** The stack currently under the mouse, whether it is in a slot, EMI's index or the NEI panel. */
public final class HoveredStack {
	private HoveredStack() {
	}

	public static EmiStack get() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen == null) {
			return EmiStack.EMPTY;
		}
		// EMI first: its index, favorites, search results and container slots are the authority.
		try {
			// Use the current cursor position rather than EMI's cached last-move position, which is
			// stale right after a screen opens and made C miss the hovered favorite.
			var interaction = EmiApi.getHoveredStack(
					(int) client.mouse.getX(), (int) client.mouse.getY(), true);
			if (interaction != null) {
				var ingredient = interaction.getStack();
				if (ingredient != null && ingredient instanceof EmiStack stack && !stack.isEmpty()) {
					return stack;
				}
				if (ingredient != null) {
					// A favorite of a recipe should craft that recipe's result, not its input.
					var context = EmiApi.getRecipeContext(ingredient);
					if (context != null && context.getOutputs() != null) {
						for (EmiStack output : context.getOutputs()) {
							if (output != null && !output.isEmpty()) {
								return output;
							}
						}
					}
					// Favorites and other wrappers are EmiIngredients, not EmiStacks; unwrap them
					// so pressing C over EMI's favorites sidebar behaves like hovering the item.
					for (EmiStack stack : ingredient.getEmiStacks()) {
						if (stack != null && !stack.isEmpty()) {
							return stack;
						}
					}
				}
			}
		} catch (Throwable ignored) {
			// EMI is not ready yet
		}
		try {
			EmiStack panelStack = dev.nei.panel.NeiPanel.get().state().hoveredStack();
			if (panelStack != null && !panelStack.isEmpty()) {
				return panelStack;
			}
		} catch (Throwable ignored) {
			// panel not initialised
		}
		return EmiStack.EMPTY;
	}
}