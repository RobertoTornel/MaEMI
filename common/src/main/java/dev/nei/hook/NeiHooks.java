package dev.nei.hook;

import dev.nei.NeiKeybinds;
import dev.nei.config.NeiConfig;
import dev.nei.overlay.CalcResult;
import dev.nei.overlay.CalcWindow;
import dev.nei.overlay.SearchBarResult;
import dev.nei.util.StatusLine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;

/**
 * Single entry point used by the mixins so the injection code stays trivial and can be audited.
 *
 * <p>EMI owns the item index, so MaEMI does not draw a second one. What is drawn here is only the
 * calculator: the value at the end of EMI's search bar, the floating result window, and the status
 * line. Input is taken only when it lands on the window, so EMI keeps every click and key that is not
 * explicitly ours.</p>
 *
 * <p>Draw ordering is the whole point of the two render hooks: {@code Screen.renderWithTooltip} runs
 * after {@code Screen.render} and after tooltips, and {@code HandledScreen.render} runs after the slot
 * grid and after EMI's own sidebar render. Drawing from both would double-draw, so
 * {@code HandledScreen} is drawn from its own hook and every other screen from the {@code Screen}
 * one.</p>
 */
public final class NeiHooks {
	private NeiHooks() {
	}

	/** Drawn at the tail of {@code Screen.renderWithTooltip}; container screens are handled separately. */
	public static void render(DrawContext context, Screen screen, int mouseX, int mouseY, float delta) {
		if (screen == null || screen instanceof HandledScreen<?>) {
			return;
		}
		renderOverlay(context, screen, mouseX, mouseY);
	}

	/** Drawn at the tail of {@code HandledScreen.render}, i.e. above the item grid and EMI's sidebars. */
	public static void renderHandled(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY) {
		if (screen == null) {
			return;
		}
		renderOverlay(context, screen, mouseX, mouseY);
	}

	private static void renderOverlay(DrawContext context, Screen screen, int mouseX, int mouseY) {
		try {
			if (context == null || !enabled()) {
				return;
			}
			TextRenderer textRenderer = SearchBarResult.textRenderer();
			if (textRenderer == null) {
				return;
			}
			// Applies the persisted open/closed state the first time a screen is drawn.
			CalcWindow.get().restoreOpenState();
			SearchBarResult.observe();
			renderStatusLine(context, screen, textRenderer);
			// EMI's search widget draws inside Screen.render, and the item grid inside
			// HandledScreen.render, so both are already on the buffer by the time we get here.
			SearchBarResult.render(context, textRenderer);
			CalcWindow.get().render(context, textRenderer, screen.width, screen.height, mouseX, mouseY);
		} catch (Throwable ignored) {
			// An overlay must never be able to break the screen underneath it.
		}
	}

	private static void renderStatusLine(DrawContext context, Screen screen, TextRenderer textRenderer) {
		String status = StatusLine.current();
		if (status == null || status.isEmpty()) {
			return;
		}
		int width = textRenderer.getWidth(status);
		int x = Math.max(2, (screen.width - width) / 2);
		int y = screen.height - 28;
		context.fill(x - 3, y - 2, x + width + 3, y + 10, 0xC0101010);
		context.drawTextWithShadow(textRenderer, status, x, y, 0xFFFFFF);
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (screen == null || !enabled()) {
			return false;
		}
		try {
			return CalcWindow.get().mouseClicked(screen.width, screen.height, mouseX, mouseY, button);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
		if (screen == null) {
			return false;
		}
		try {
			return CalcWindow.get().mouseDragged(screen.width, screen.height, mouseX, mouseY, button);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean mouseReleased(Screen screen, double mouseX, double mouseY, int button) {
		if (screen == null) {
			return false;
		}
		try {
			return CalcWindow.get().mouseReleased(mouseX, mouseY, button);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		return false;
	}

	/**
	 * Character input reaches here through the keyboard mixin. Only the window's rename label wants
	 * characters; EMI's search field gets everything else, including when the calculator owns focus.
	 */
	public static boolean charTyped(Screen screen, char chr, int modifiers) {
		if (!enabled() || !CalcResult.visible() || !CalcWindow.get().editing()) {
			return false;
		}
		try {
			return CalcWindow.get().charTyped(chr);
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (screen == null) {
			return false;
		}
		return keyPressedInternal(screen, keyCode, scanCode, modifiers);
	}

	/** Shared by both screen mixins; kept separate so {@code screen instanceof} checks stay obvious. */
	public static boolean keyPressedInternal(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (!enabled()) {
			return false;
		}
		try {
			// While the rename label is open it owns Enter/Escape/Backspace, so a rename cannot also
			// trigger a key bind or close the inventory.
			if (CalcWindow.get().editing() && CalcResult.visible()) {
				return CalcWindow.get().keyPressed(keyCode);
			}
		} catch (Throwable ignored) {
		}
		// Settings/dump/autocraft stay reachable from a container GUI; EMI's own keys are never
		// intercepted. Typing into any text field (EMI search, rename box, anvil) always wins.
		if (!(screen instanceof HandledScreen<?>)) {
			return false;
		}
		if (screen.getFocused() instanceof net.minecraft.client.gui.widget.TextFieldWidget) {
			return false;
		}
		try {
			return NeiKeybinds.handleKeyPressed(keyCode, scanCode, modifiers);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** MaEMI's master switch. The calculator follows it so one toggle silences the whole mod. */
	private static boolean enabled() {
		if (!NeiConfig.get().general.enabled) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.currentScreen != null;
	}

	/** Exposed for the mixin that has to know whether the rename label is eating characters. */
	public static boolean editingLabel() {
		try {
			return CalcResult.visible() && CalcWindow.get().editing();
		} catch (Throwable ignored) {
			return false;
		}
	}
}
