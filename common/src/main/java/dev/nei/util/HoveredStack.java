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
		try {
			var interaction = EmiApi.getHoveredStack(true);
			if (interaction != null && interaction.getStack() instanceof EmiStack stack && !stack.isEmpty()) {
				return stack;
			}
		} catch (Throwable ignored) {
			// EMI is not ready yet
		}
		return EmiStack.EMPTY;
	}
}
