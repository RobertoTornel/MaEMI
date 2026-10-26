package dev.nei.overlay;

import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import dev.nei.config.NeiConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import dev.nei.render.DrawContext;

/**
 * Draws the evaluated result at the right end of EMI's search bar.
 *
 * <p>EMI owns that field, so this deliberately draws over it rather than adding a widget: a second
 * text field would take focus, swallow typing, and fight EMI's own layout. Nothing here is clickable,
 * and nothing consumes input — the value is a label, drawn dim and clipped to the field's bounds.
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

	/**
	 * Draws the value right-aligned inside EMI's field.
	 *
	 * <p>Text is right-aligned against the field's own right edge and only drawn when it fits in the
	 * space the query leaves free; a value that would overlap the query is shortened rather than
	 * spilling out of the field.</p>
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
			int left = search.getX() + INSET;
			int right = search.getX() + search.getWidth() - INSET;
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
			int y = search.getY() + (search.getHeight() - 8) / 2;
			// Dim, but bright enough to read on the field's dark background.
			context.drawText(textRenderer, shown, x, y, 0xFF9AA79A, false);
		} catch (Throwable ignored) {
			// A failed overlay must never take the screen down with it.
		}
	}

	/** The client text renderer, or null before the client is ready. */
	public static TextRenderer textRenderer() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null ? null : client.textRenderer;
	}
}
