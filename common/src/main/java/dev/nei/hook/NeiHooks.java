package dev.nei.hook;

import dev.nei.NeiKeybinds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Single entry point used by the mixins so the injection code stays trivial and can be audited.
 *
 * <p>The custom item panel is retired. EMI's own index is the item list, and this mod must not
 * draw a second one, steal its mouse input, or consume its keys. Only the mod's own settings key
 * (and the dump key) are still routed from container screens; everything else goes to EMI.</p>
 */
public final class NeiHooks {
	private NeiHooks() {
	}

	public static void render(DrawContext context, Screen screen, int mouseX, int mouseY, float delta) {
		// No custom panel: EMI owns the screen space.
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		return false;
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		return false;
	}

	public static boolean charTyped(Screen screen, char chr, int modifiers) {
		return false;
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		// Settings/dump stay reachable from a container GUI; EMI's own keys are never intercepted.
		if (!(screen instanceof HandledScreen<?>)) {
			return false;
		}
		return NeiKeybinds.handleKeyPressed(keyCode, scanCode, modifiers);
	}
}
