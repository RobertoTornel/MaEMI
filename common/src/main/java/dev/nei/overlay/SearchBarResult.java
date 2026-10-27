package dev.nei.overlay;

import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import dev.nei.config.NeiConfig;
import dev.nei.mixin.ClickableWidgetAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws the evaluated result at the right end of EMI's search bar.
 *
 * <p>EMI owns that field, so this deliberately draws over it rather than adding a widget: a second
 * text field would take focus, swallow typing, and fight EMI's own layout. The value is a small,
 * dim label drawn inside the field's right edge; clicking it pins the calculation and opens the
 * floating window. Nothing here consumes input — EMI keeps every keystroke.</p>
 *
 * <p>The value is read indirectly: {@link #observe()} pulls the field's current text once per frame
 * from {@link EmiScreenManager#search}. Reading the widget's text is the least invasive way to stay
 * live as the player types; EMI calls its own search on the same text in the same frame.</p>
 */
public final class SearchBarResult {
	private SearchBarResult() {
	}

	/** The width of the padding EMI's field keeps on both sides. */
	private static final int INSET = 4;
	/** Leave the caret room at the right edge when the query is long. */
	private static final int RESULT_GAP = 3;
	/** The search-bar result is drawn small: just the value, never the expression. */
	private static final int PADDING_RIGHT = 2;

	/**
	 * Feeds the current search text into {@link CalcResult}. Safe to call every frame and from any
	 * hook; it never throws.
	 */
	public static void observe() {
		try {
			EmiSearchWidget search = EmiScreenManager.search;
			if (search == null) {
				return;
			}
			CalcResult.update(search.getText());
		} catch (Throwable ignored) {
			// EMI is mid-reload or the field moved: showing no result is the correct fallback.
		}
	}

	/** True when the field is on screen and has a value to show. */
	public static boolean shouldDraw() {
		try {
			EmiSearchWidget search = EmiScreenManager.search;
			if (search == null || !search.isVisible()) {
				return false;
			}
			if (!NeiConfig.get().calculator.showInSearchBar) {
				return false;
			}
			return CalcResult.hasValue();
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** True when the mouse is over the drawn result label, so clicks can pin it. */
	public static boolean isMouseOver(double mouseX, double mouseY) {
		try {
			EmiSearchWidget search = EmiScreenManager.search;
			if (search == null || !shouldDraw()) {
				return false;
			}
			String value = CalcResult.text();
			if (value == null || value.isEmpty()) {
				return false;
			}
			int searchX = ((ClickableWidgetAccessor) search).nei$getX();
			int left = searchX + INSET;
			int right = searchX + search.getWidth() - INSET - PADDING_RIGHT;
			int available = right - left;
			if (available <= 8) {
				return false;
			}
			String query = search.getText();
			int queryWidth = query == null || query.isEmpty() ? 0 : textRendererWidth(query);
			int free = available - queryWidth - RESULT_GAP;
			if (free <= 8) {
				return false;
			}
			String shown = trimToWidth(value, free);
			if (shown.isEmpty()) {
				return false;
			}
			int textWidth = textRendererWidth(shown);
			int x = right - textWidth;
			int y = ((ClickableWidgetAccessor) search).nei$getY();
			int height = search.getHeight();
			return mouseX >= x && mouseX < x + textWidth && mouseY >= y && mouseY < y + height;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Pin the calculation and open the floating window. Called when the search-bar label is clicked. */
	public static void onClick() {
		CalcResult.togglePin();
	}

	/**
	 * Draws the value right-aligned inside EMI's field.
	 *
	 * <p>The result is drawn small: just the number, dimmed, right-aligned inside the field.
	 * When the query is long there may be no room — in that case nothing is drawn rather than
	 * overprinting the query.</p>
	 */
	public static void render(DrawContext context, TextRenderer textRenderer) {
		if (context == null || textRenderer == null || !shouldDraw()) {
			return;
		}
		try {
			EmiSearchWidget search = EmiScreenManager.search;
			String value = CalcResult.text();
			if (value == null || value.isEmpty()) {
				return;
			}
			int searchX = ((ClickableWidgetAccessor) search).nei$getX();
			int left = searchX + INSET;
			int right = searchX + search.getWidth() - INSET - PADDING_RIGHT;
			int available = right - left;
			if (available <= 8) {
				return;
			}

			// Keep clear of the query itself so the two never overprint each other.
			String query = search.getText();
			int queryWidth = query == null || query.isEmpty() ? 0 : textRenderer.getWidth(query);
			int free = available - queryWidth - RESULT_GAP;
			if (free <= 8) {
				return;
			}
			String shown = textRenderer.trimToWidth(value, free);
			if (shown.isEmpty()) {
				return;
			}
			int textWidth = textRenderer.getWidth(shown);
			int x = right - textWidth;
			int y = ((ClickableWidgetAccessor) search).nei$getY() + (search.getHeight() - 8) / 2;
			// Dim, but bright enough to read on the field's dark background.
			context.drawText(textRenderer, shown, x, y, 0xFF9AA79A, false);
		} catch (Throwable ignored) {
			// A failed overlay must never take the screen down with it.
		}
	}

	private static int textRendererWidth(String text) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return 0;
		return client.textRenderer.getWidth(text);
	}

	private static String trimToWidth(String text, int width) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return text;
		return client.textRenderer.trimToWidth(text, width);
	}

	/** The client text renderer, or null before the client is ready. */
	public static TextRenderer textRenderer() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null ? null : client.textRenderer;
	}
}
