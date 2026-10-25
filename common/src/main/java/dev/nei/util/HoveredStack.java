package dev.nei.util;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

/** The stack currently under the mouse, whether it is in a slot, EMI's index or the MaEMI panel. */
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
			Nei.LOG.warn("[MaEMI] could not resolve hovered ingredient", t);
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
			Nei.LOG.warn("[MaEMI] cursor hover lookup failed", t);
		}
		// Container slots are the final fallback. This catches screens whose item list is made of
		// slots rather than EMI widgets, notably AE2's storage/crafting terminals.
		if (client.currentScreen instanceof HandledScreen<?> screen) {
			try {
				HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
				Slot focused = accessor.nei$getFocusedSlot();
				if (focused != null && focused.hasStack()) {
					EmiStack stack = EmiStack.of(focused.getStack());
					if (!stack.isEmpty()) {
						return stack;
					}
				}
				// Some screens (AE2's terminal repo in particular) draw their item list as slots
				// without updating vanilla's focusedSlot. Look up the slot under the cursor.
				double scale = Math.max(1.0, client.getWindow().getScaleFactor());
				int mouseX = (int) (client.mouse.getX() / scale);
				int mouseY = (int) (client.mouse.getY() / scale);
				for (Slot slot : screen.getScreenHandler().slots) {
					if (!slot.hasStack()) {
						continue;
					}
					int slotX = accessor.nei$getX() + slot.x;
					int slotY = accessor.nei$getY() + slot.y;
					if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
						EmiStack stack = EmiStack.of(slot.getStack());
						if (!stack.isEmpty()) {
							return stack;
						}
					}
				}
			} catch (Throwable t) {
				Nei.LOG.warn("[MaEMI] focused-slot hover lookup failed", t);
			}
		}

		return EmiStack.EMPTY;
	}
}