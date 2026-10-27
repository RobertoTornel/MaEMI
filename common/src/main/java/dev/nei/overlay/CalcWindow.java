package dev.nei.overlay;

import java.util.List;
import dev.nei.config.NeiConfig;
import dev.nei.config.NeiConfigData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import dev.nei.render.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.LiteralText;

/**
 * The small floating window that keeps a calculation on screen.
 *
 * <p>The search bar result is only visible while the query is being typed, and it sits inside a field
 * that EMI owns; the player asked for something they can move out of the way and keep. So this is a
 * separate, draggable window drawn after everything else, with its own name: dragging it reveals an
 * inline editable label, because "rename it" and "move it" are the same gesture in the player's head.
 *
 * <p>Everything about it is persisted in {@code config/nei.json} under {@code calculator}. The fields
 * are read through {@link #config()} on every use and written back on drop/close/rename, so a window
 * closed in one session comes back as it was left.</p>
 *
 * <p>Input is deliberately self-contained: it does not register a vanilla widget, because a widget
 * would take focus away from EMI's search field and would have to be added to screens this mod does
 * not own. The title bar, the close button and the label are hit-tested here instead, and only clicks
 * that land on the window are consumed.</p>
 */
public final class CalcWindow {
	/** Shared with the retired panel's palette so the overlay looks like the rest of the mod. */
	private static final int BACKGROUND = 0xE0101010;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int TITLE_BACKGROUND = 0xFF1A1A1A;
	private static final int INNER = 0xFF1E1E1E;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF8A8A8A;
	private static final int VALUE_TEXT = 0xFFB9E4B4;
	private static final int CLOSE_BACKGROUND = 0xFF3A1E1E;
	private static final int CLOSE_HOVER = 0xFF8E2A2A;
	private static final int CLOSE_GLYPH = 0xFFE8B0B0;
	private static final int PIN_BACKGROUND = 0xFF1E3A1E;
	private static final int PIN_HOVER = 0xFF2E5A2E;
	private static final int PIN_GLYPH = 0xFFB0E8B0;
	private static final int LABEL_BACKGROUND = 0xFF101010;

	public static final int TITLE_HEIGHT = 14;
	/** Big enough to hit without aiming: a 9px glyph is what the brief called out as too small. */
	public static final int CLOSE_SIZE = 13;
	/** The pin button is the same size so it is easy to hit. */
	public static final int PIN_SIZE = 13;
	/** Small window: just the number, nothing more. */
	public static final int MIN_WIDTH = 72;
	public static final int MAX_WIDTH = 240;
	private static final int MIN_HEIGHT = 30;
	private static final int MAX_HEIGHT = 80;
	private static final int PADDING = 4;
	private static final int DEFAULT_WIDTH = 72;
	private static final int DEFAULT_HEIGHT = 30;

	private static final CalcWindow INSTANCE = new CalcWindow();

	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean editing;
	private String draft = "";
	private boolean initialised;
	private boolean restoredOpenState;

	/** The window rectangle for the frame being drawn; used by both hit testing and rendering. */
	private int frameX;
	private int frameY;
	private int frameWidth = DEFAULT_WIDTH;
	private int frameHeight = DEFAULT_HEIGHT;

	private CalcWindow() {
	}

	public static CalcWindow get() {
		return INSTANCE;
	}

	private static NeiConfigData.Calculator config() {
		try {
			NeiConfigData data = NeiConfig.get();
			if (data.calculator == null) {
				data.calculator = new NeiConfigData.Calculator();
			}
			return data.calculator;
		} catch (Throwable ignored) {
			return new NeiConfigData.Calculator();
		}
	}

	private static void save() {
		try {
			NeiConfig.save();
		} catch (Throwable ignored) {
			// A window position is never worth taking the client down for.
		}
	}

	/** Applies persisted size and name once, so the rest of the class can read them directly. */
	private void ensureInitialised() {
		if (initialised) {
			return;
		}
		initialised = true;
		NeiConfigData.Calculator cfg = config();
		frameWidth = clamp(cfg.width, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH);
		frameHeight = clamp(cfg.height, MIN_HEIGHT, MAX_HEIGHT, DEFAULT_HEIGHT);
		if (draft.isEmpty()) {
			draft = displayName(cfg);
		}
	}

	private static int clamp(int value, int min, int max, int fallback) {
		int candidate = value <= 0 ? fallback : value;
		return Math.max(min, Math.min(max, candidate));
	}

	private static String displayName(NeiConfigData.Calculator cfg) {
		if (cfg.name == null || cfg.name.isBlank()) {
			return new TranslatableText("nei.calc.default_name").getString();
		}
		return cfg.name.trim();
	}

	public String name() {
		return displayName(config());
	}

	/** The current editable text: the draft while renaming, the stored name otherwise. */
	public String editedName() {
		return editing ? draft : name();
	}

	public boolean editing() {
		return editing;
	}

	// ------------------------------------------------------------------ geometry

	/** Clamps the stored position into the given screen and remembers it for the frame. */
	private void layout(int screenWidth, int screenHeight) {
		ensureInitialised();
		NeiConfigData.Calculator cfg = config();
		frameWidth = clamp(cfg.width, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH);
		frameHeight = clamp(cfg.height, MIN_HEIGHT, MAX_HEIGHT, DEFAULT_HEIGHT);
		int x = cfg.x;
		int y = cfg.y;
		if (x < 0 || y < 0) {
			// First use: sit above the hotbar on the right, clear of EMI's centred search bar.
			x = screenWidth - frameWidth - 6;
			y = screenHeight - frameHeight - 44;
		}
		frameX = clampToScreen(x, frameWidth, screenWidth);
		frameY = clampToScreen(y, frameHeight, screenHeight);
	}

	private static int clampToScreen(int value, int size, int screenSize) {
		int max = Math.max(0, screenSize - size);
		return Math.max(0, Math.min(max, value));
	}

	private static int closeX(int x, int width) {
		return x + width - CLOSE_SIZE - 2;
	}

	private static int closeY(int y) {
		return y + (TITLE_HEIGHT - CLOSE_SIZE) / 2;
	}

	private static int pinX(int x, int width) {
		return x + width - CLOSE_SIZE - PIN_SIZE - 4;
	}

	private static int pinY(int y) {
		return y + (TITLE_HEIGHT - PIN_SIZE) / 2;
	}

	private boolean insidePin(double mouseX, double mouseY) {
		int px = pinX(frameX, frameWidth);
		int py = pinY(frameY);
		return mouseX >= px && mouseX < px + PIN_SIZE && mouseY >= py && mouseY < py + PIN_SIZE;
	}

	private boolean insideTitle(double mouseX, double mouseY) {
		return mouseX >= frameX && mouseX < frameX + frameWidth
				&& mouseY >= frameY && mouseY < frameY + TITLE_HEIGHT;
	}

	private boolean insideClose(double mouseX, double mouseY) {
		int cx = closeX(frameX, frameWidth);
		int cy = closeY(frameY);
		return mouseX >= cx && mouseX < cx + CLOSE_SIZE && mouseY >= cy && mouseY < cy + CLOSE_SIZE;
	}

	private boolean insideLabel(double mouseX, double mouseY) {
		return mouseX >= frameX + PADDING && mouseX < closeX(frameX, frameWidth) - 2
				&& mouseY >= frameY + 1 && mouseY < frameY + TITLE_HEIGHT - 1;
	}

	private boolean insideWindow(double mouseX, double mouseY) {
		return mouseX >= frameX && mouseX < frameX + frameWidth
				&& mouseY >= frameY && mouseY < frameY + frameHeight;
	}

	// ------------------------------------------------------------------ input

	/**
	 * Click handling. Returns true only when the click belongs to this window, so EMI keeps every
	 * click that lands anywhere else. Called before EMI sees the event.
	 */
	public boolean mouseClicked(int screenWidth, int screenHeight, double mouseX, double mouseY, int button) {
		layout(screenWidth, screenHeight);
		if (!CalcResult.visible()) {
			return false;
		}
		if (!insideWindow(mouseX, mouseY)) {
			// Clicking away finishes a rename rather than silently discarding it.
			if (editing) {
				commitRename();
			}
			return false;
		}
		if (insideClose(mouseX, mouseY)) {
			CalcResult.dismiss();
			commitRename();
			NeiConfigData.Calculator cfg = config();
			cfg.open = false;
			save();
			return true;
		}
		if (insidePin(mouseX, mouseY)) {
			CalcResult.togglePin();
			commitRename();
			return true;
		}
		// Clicking the value area toggles showing the expression beneath it.
		int valueLeft = frameX + PADDING;
		int valueRight = frameX + frameWidth - PADDING;
		int valueTop = frameY + TITLE_HEIGHT;
		int valueBottom = frameY + frameHeight;
		if (mouseX >= valueLeft && mouseX < valueRight && mouseY >= valueTop && mouseY < valueBottom
				&& !insideClose(mouseX, mouseY) && !insidePin(mouseX, mouseY)) {
			CalcResult.toggleExpression();
			return true;
		}
		if (insideTitle(mouseX, mouseY)) {
			if (button == 0) {
				dragging = true;
				dragOffsetX = (int) mouseX - frameX;
				dragOffsetY = (int) mouseY - frameY;
				// Dragging is how the window is renamed: reveal the label already focused.
				beginRename();
			}
			return true;
		}
		return true;
	}

	public boolean mouseDragged(int screenWidth, int screenHeight, double mouseX, double mouseY, int button) {
		if (!dragging) {
			return false;
		}
		frameX = clampToScreen((int) mouseX - dragOffsetX, frameWidth, screenWidth);
		frameY = clampToScreen((int) mouseY - dragOffsetY, frameHeight, screenHeight);
		return true;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		NeiConfigData.Calculator cfg = config();
		cfg.x = frameX;
		cfg.y = frameY;
		save();
		return true;
	}

	/** Keyboard for the inline label. Returns true when the key was used here. */
	public boolean keyPressed(int keyCode) {
		if (!editing) {
			return false;
		}
		switch (keyCode) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
				commitRename();
				return true;
			}
			case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
				// Escape abandons the edit; the old name stays.
				editing = false;
				draft = name();
				return true;
			}
			case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
				if (!draft.isEmpty()) {
					draft = draft.substring(0, draft.length() - 1);
				}
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	public boolean charTyped(char chr) {
		if (!editing || Character.isISOControl(chr)) {
			return false;
		}
		if (draft.length() < 32) {
			draft = draft + chr;
		}
		return true;
	}

	private void beginRename() {
		editing = true;
		draft = name();
	}

	private void commitRename() {
		if (!editing) {
			return;
		}
		editing = false;
		String trimmed = draft == null ? "" : draft.trim();
		NeiConfigData.Calculator cfg = config();
		cfg.name = trimmed.isEmpty() ? "" : trimmed;
		draft = displayName(cfg);
		save();
	}

	/** True while a drag or rename owns the mouse/keyboard, so other input must stay out of the way. */
	public boolean isBusy() {
		return dragging || editing;
	}

	// ------------------------------------------------------------------ rendering

	public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight,
			int mouseX, int mouseY) {
		if (context == null || textRenderer == null || !CalcResult.visible()) {
			return;
		}
		layout(screenWidth, screenHeight);

		int x = frameX;
		int y = frameY;
		int right = x + frameWidth;
		int bottom = y + frameHeight;

		context.fill(x, y, right, bottom, BACKGROUND);
		context.fill(x, y, right, y + TITLE_HEIGHT, TITLE_BACKGROUND);
		context.drawBorder(x, y, frameWidth, frameHeight, BORDER);
		context.fill(x + 1, y + TITLE_HEIGHT, right - 1, y + TITLE_HEIGHT + 1, BORDER);

		renderTitle(context, textRenderer, mouseX, mouseY);
		renderValue(context, textRenderer, mouseX, mouseY, bottom);
	}

	private void renderTitle(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
		int x = frameX;
		int y = frameY;
		int labelRight = closeX(x, frameWidth) - 3;
		int labelLeft = x + PADDING;
		String title = editedName();

		if (editing) {
			context.fill(labelLeft - 1, y + 1, labelRight, y + TITLE_HEIGHT - 1, LABEL_BACKGROUND);
			String shown = textRenderer.trimToWidth(title, Math.max(1, labelRight - labelLeft - 4));
			context.drawText(textRenderer, shown, labelLeft + 2, y + 4, TEXT, false);
			int caret = labelLeft + 2 + textRenderer.getWidth(shown);
			context.fill(Math.min(caret, labelRight - 1), y + 3, Math.min(caret + 1, labelRight), y + TITLE_HEIGHT - 2, TEXT);
		} else {
			String shown = textRenderer.trimToWidth(title, Math.max(1, labelRight - labelLeft - 2));
			context.drawText(textRenderer, shown, labelLeft, y + 4, TEXT_DIM, false);
		}

		boolean hovered = insideClose(mouseX, mouseY);
		int cx = closeX(x, frameWidth);
		int cy = closeY(y);
		context.fill(cx, cy, cx + CLOSE_SIZE, cy + CLOSE_SIZE, hovered ? CLOSE_HOVER : CLOSE_BACKGROUND);
		context.drawBorder(cx, cy, CLOSE_SIZE, CLOSE_SIZE, hovered ? CLOSE_GLYPH : BORDER);
		// A drawn X rather than a glyph: the font's "x" is 5px tall and reads as decoration.
		int inset = 3;
		for (int i = 0; i < CLOSE_SIZE - inset * 2; i++) {
			int px = cx + inset + i;
			int py = cy + inset + i;
			context.fill(px, py, px + 1, py + 1, CLOSE_GLYPH);
			int mirror = cy + CLOSE_SIZE - inset - 1 - i;
			context.fill(px, mirror, px + 1, mirror + 1, CLOSE_GLYPH);
		}
		if (hovered) {
			context.drawTooltip(textRenderer, List.of(new TranslatableText("nei.calc.close")), mouseX, mouseY);
		}

		boolean pinHovered = insidePin(mouseX, mouseY);
		int px = pinX(x, frameWidth);
		int py = pinY(y);
		context.fill(px, py, px + PIN_SIZE, py + PIN_SIZE, pinHovered ? PIN_HOVER : PIN_BACKGROUND);
		context.drawBorder(px, py, PIN_SIZE, PIN_SIZE, pinHovered ? PIN_GLYPH : BORDER);
		// Draw a simple pin glyph: a vertical line with a small head.
		int pinInset = 3;
		for (int i = 0; i < PIN_SIZE - pinInset * 2; i++) {
			context.fill(px + pinInset + i, py + pinInset, px + pinInset + i + 1, py + pinInset + 1, PIN_GLYPH);
		}
		context.fill(px + pinInset, py + pinInset, px + pinInset + 1, py + pinInset + PIN_SIZE - pinInset * 2, PIN_GLYPH);
		if (pinHovered) {
			context.drawTooltip(textRenderer, List.of(new TranslatableText(CalcResult.isPinned() ? "nei.calc.unpin" : "nei.calc.pin")), mouseX, mouseY);
		}
	}

	private void renderValue(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int bottom) {
		int left = frameX + PADDING;
		int right = frameX + frameWidth - PADDING;
		int available = Math.max(1, right - left);

		String value = CalcResult.text();
		String shownValue = textRenderer.trimToWidth(value, available);
		int valueY = frameY + TITLE_HEIGHT + 5;
		context.drawText(textRenderer, shownValue, left, valueY, VALUE_TEXT, false);

		String expression = CalcResult.expression();
		if (!expression.isEmpty()) {
			int exprY = Math.min(bottom - 11, valueY + 12);
			String shownExpression = textRenderer.trimToWidth(expression, available);
			context.drawText(textRenderer, shownExpression, left, exprY, TEXT_DIM, false);
		}

		if (!insideWindow(mouseX, mouseY)) {
			return;
		}
		// Hovering the value shows the full digits, which is the point of the window for long numbers.
		if (mouseY >= valueY - 2 && mouseY < valueY + 10) {
			context.drawTooltip(textRenderer, List.of(new LiteralText(value)), mouseX, mouseY);
		}
	}

	/** Restores the remembered open/closed state once, the first time a screen is drawn. */
	public void restoreOpenState() {
		if (restoredOpenState) {
			return;
		}
		restoredOpenState = true;
		CalcResult.restore(config().open);
	}
}
