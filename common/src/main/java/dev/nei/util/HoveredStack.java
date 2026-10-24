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
			var interaction = EmiApi.getHoveredStack(true);
			if (interaction != null) {
				var ingredient = interaction.getStack();
				if (ingredient instanceof EmiStack stack) {
					if (!stack.isEmpty()) {
						return stack;
					}
				} else if (ingredient != null) {
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