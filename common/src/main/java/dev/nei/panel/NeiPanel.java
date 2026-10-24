package dev.nei.panel;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * The NEI item panel. One instance lives for the lifetime of the client; state such as the current
 * page and the search field is preserved while moving between screens when the config asks for it.
 */
public final class NeiPanel {
	private static final NeiPanel INSTANCE = new NeiPanel();

	private NeiPanel() {
	}

	public static NeiPanel get() {
		return INSTANCE;
	}

	public void render(DrawContext context, Screen screen, int mouseX, int mouseY, float delta) {
	}

	public boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		return false;
	}

	public boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		return false;
	}

	public boolean charTyped(Screen screen, char chr, int modifiers) {
		return false;
	}
}
