package dev.nei.panel;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.config.NeiConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * The NEI item panel. One instance lives for the lifetime of the client; state such as the current
 * page and the search field is preserved while moving between screens when the config asks for it.
 *
 * <p>This class only wires the pieces together: {@link NeiPanelLayout} computes geometry,
 * {@link ItemPanelSource} owns the filtered list, {@link NeiPanelRenderer} draws and
 * {@link NeiPanelInput} handles interaction.</p>
 */
public final class NeiPanel {
	private static final NeiPanel INSTANCE = new NeiPanel();

	private final NeiPanelState state = new NeiPanelState();
	private Screen lastScreen;

	private NeiPanel() {
	}

	public static NeiPanel get() {
		return INSTANCE;
	}

	public NeiPanelState state() {
		return state;
	}

	/** Builds the geometry for the given screen. Cheap enough to call from render and input alike. */
	public NeiPanelLayout layout(Screen screen) {
		List<EmiStack> stacks = ItemPanelSource.get().stacks();
		return new NeiPanelLayout(screen, state, stacks.size());
	}

	public void onScreenChanged(Screen screen) {
		if (screen == lastScreen) {
			return;
		}
		lastScreen = screen;
		boolean remember = true;
		try {
			remember = NeiConfig.get().panel.rememberSearch;
		} catch (Throwable ignored) {
		}
		state.onScreenChanged(remember);
		ItemPanelSource.get().invalidate();
	}

	public boolean isFocused() {
		return state.isFocused();
	}

	public void refresh() {
		ItemPanelSource.get().invalidate();
	}

	public void render(DrawContext context, Screen screen, int mouseX, int mouseY, float delta) {
		if (screen == null || !enabled()) {
			return;
		}
		onScreenChanged(screen);
		NeiPanelLayout layout = layout(screen);
		NeiPanelRenderer.render(context, screen, layout, state, ItemPanelSource.get().stacks(),
				mouseX, mouseY, delta);
	}

	public boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (screen == null || !enabled()) {
			return false;
		}
		onScreenChanged(screen);
		return NeiPanelInput.mouseClicked(screen, layout(screen), state, ItemPanelSource.get().stacks(),
				mouseX, mouseY, button);
	}

	public boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		if (screen == null || !enabled()) {
			return false;
		}
		onScreenChanged(screen);
		return NeiPanelInput.mouseScrolled(screen, layout(screen), state, ItemPanelSource.get().stacks(),
				mouseX, mouseY, amount);
	}

	public boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (screen == null || !enabled()) {
			return false;
		}
		onScreenChanged(screen);
		return NeiPanelInput.keyPressed(screen, layout(screen), state, keyCode, scanCode, modifiers);
	}

	public boolean charTyped(Screen screen, char chr, int modifiers) {
		if (screen == null || !enabled()) {
			return false;
		}
		onScreenChanged(screen);
		return NeiPanelInput.charTyped(screen, state, chr, modifiers);
	}

	private boolean enabled() {
		if (!state.isEnabled()) {
			return false;
		}
		try {
			return NeiConfig.get().general.enabled && NeiConfig.get().panel.enabled;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
