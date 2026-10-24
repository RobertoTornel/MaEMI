package dev.nei.hook;

import dev.nei.config.NeiConfig;
import dev.nei.panel.NeiPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Single entry point used by the mixins so the injection code stays trivial and can be audited.
 */
public final class NeiHooks {
	private NeiHooks() {
	}

	private static boolean active(Screen screen) {
		return NeiConfig.get().general.enabled && NeiConfig.get().panel.enabled
			&& (NeiConfig.get().general.showEverywhere || screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>);
	}

	public static void render(DrawContext context, Screen screen, int mouseX, int mouseY, float delta) {
		if (active(screen)) {
			NeiPanel.get().render(context, screen, mouseX, mouseY, delta);
		}
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		return active(screen) && NeiPanel.get().mouseClicked(screen, mouseX, mouseY, button);
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		return active(screen) && NeiPanel.get().mouseScrolled(screen, mouseX, mouseY, amount);
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		return active(screen) && NeiPanel.get().keyPressed(screen, keyCode, scanCode, modifiers);
	}

	public static boolean charTyped(Screen screen, char chr, int modifiers) {
		return active(screen) && NeiPanel.get().charTyped(screen, chr, modifiers);
	}
}
