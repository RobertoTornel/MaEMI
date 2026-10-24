package dev.nei.panel;

import java.util.ArrayList;
import java.util.List;

import dev.nei.config.NeiConfig;
import dev.nei.config.NeiConfigData;
import dev.nei.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;

/**
 * Immutable geometry for one frame of the NEI item panel. Built from the open screen, the config and
 * the current state so that rendering, hit testing and scrolling all agree on where things are.
 *
 * <p>The panel is anchored to the edge of the container GUI (or the screen edge on screens without a
 * container background) with a two pixel gap, and is then clamped to stay inside the window.</p>
 */
public final class NeiPanelLayout {
	public static final int SLOT = 18;
	public static final int HEADER = 18;
	public static final int FOOTER = 18;
	public static final int PADDING = 2;
	public static final int GAP = 2;
	public static final int MARGIN = 2;
	public static final int HELP_SIZE = 9;

	public final int screenWidth;
	public final int screenHeight;

	public final int panelX;
	public final int panelY;
	public final int panelWidth;
	public final int panelHeight;

	public final int columns;
	public final int rows;
	public final boolean showSearch;
	public final boolean showTabs;
	public final boolean showCounts;
	public final NeiConfigData.Side side;

	public final int gridX;
	public final int gridY;

	public final int searchX;
	public final int searchY;
	public final int searchWidth;
	public final int searchHeight;

	public final int helpX;
	public final int helpY;

	public final int pageX;
	public final int pageY;
	public final int pageWidth;
	public final String pageLabel;

	public final List<Tab> tabs;

	/** A clickable subset tab in the footer. */
	public static final class Tab {
		public final String label;
		public final String subset;
		public final int x;
		public final int y;
		public final int width;
		public final int height;

		Tab(String label, String subset, int x, int y, int width, int height) {
			this.label = label;
			this.subset = subset;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	private static final class Bounds {
		final int x;
		final int y;
		final int width;
		final int height;

		Bounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	public NeiPanelLayout(Screen screen, NeiPanelState state, int totalItems) {
		NeiConfigData config = NeiConfig.get();
		NeiConfigData.Panel panel = config.panel;

		this.screenWidth = screen == null ? 320 : Math.max(1, screen.width);
		this.screenHeight = screen == null ? 240 : Math.max(1, screen.height);

		this.showSearch = panel.showSearchBar;
		this.showTabs = panel.showTabs;
		this.showCounts = panel.showItemCount;
		this.side = state == null ? panel.side : state.effectiveSide(panel.side);

		// Prefer to sit next to the GUI without covering it: if the configured width does not fit
		// on that side, use fewer columns rather than overlapping the container background.
		Bounds bounds = bounds(screen);
		int available = screenWidth - MARGIN * 2;
		if (bounds != null) {
			available = side == NeiConfigData.Side.LEFT
				? bounds.x - GAP - MARGIN
				: screenWidth - (bounds.x + bounds.width) - GAP - MARGIN;
		}
		int spaceColumns = Math.max(1, (available - PADDING * 2) / SLOT);
		int maxColumns = Math.max(1, (screenWidth - MARGIN * 2 - PADDING * 2) / SLOT);
		this.columns = Math.max(1, Math.min(Math.min(12, Math.min(panel.columns, spaceColumns)), maxColumns));

		int configuredRows = Math.max(1, Math.min(20, panel.rows));
		int footer = showTabs ? FOOTER : 0;
		int maxRows = Math.max(1, (screenHeight - MARGIN * 2 - HEADER - footer) / SLOT);
		this.rows = Math.max(1, Math.min(configuredRows, maxRows));

		this.panelWidth = columns * SLOT + PADDING * 2;
		this.panelHeight = HEADER + rows * SLOT + footer;

		int proposedX;
		if (side == NeiConfigData.Side.LEFT) {
			proposedX = bounds != null ? bounds.x - GAP - panelWidth : MARGIN;
		} else {
			proposedX = bounds != null ? bounds.x + bounds.width + GAP : screenWidth - MARGIN - panelWidth;
		}
		int maxX = Math.max(MARGIN, screenWidth - MARGIN - panelWidth);
		this.panelX = Math.max(MARGIN, Math.min(proposedX, maxX));

		int proposedY = bounds != null ? bounds.y : (screenHeight - panelHeight) / 2;
		int maxY = Math.max(MARGIN, screenHeight - MARGIN - panelHeight);
		this.panelY = Math.max(MARGIN, Math.min(proposedY, maxY));

		this.gridX = panelX + PADDING;
		this.gridY = panelY + HEADER;

		TextRenderer textRenderer = textRenderer();
		this.helpX = panelX + panelWidth - PADDING - HELP_SIZE;
		this.helpY = panelY + (HEADER - HELP_SIZE) / 2;

		int totalRows = totalItems <= 0 ? 0 : (totalItems + columns - 1) / columns;
		int pages = Math.max(1, (totalRows + rows - 1) / rows);
		int visiblePage = Math.min(pages, (state == null ? 0 : state.scrollRow()) / rows + 1);
		this.pageLabel = Text.translatable("nei.panel.page", visiblePage, pages).getString();
		this.pageWidth = width(textRenderer, pageLabel);

		if (showTabs) {
			this.pageX = panelX + panelWidth - PADDING - pageWidth;
			this.pageY = panelY + panelHeight - 14;
		} else {
			this.pageX = helpX - 3 - pageWidth;
			this.pageY = panelY + (HEADER - 8) / 2;
		}

		this.searchX = panelX + PADDING;
		this.searchY = panelY + 2;
		this.searchHeight = 14;
		int rightLimit = showTabs ? helpX - 3 : Math.min(helpX - 3, pageX - 3);
		this.searchWidth = Math.max(16, Math.min(panelWidth - PADDING * 2 - HELP_SIZE - 4, rightLimit - searchX));

		this.tabs = buildTabs(state, textRenderer);
	}

	private List<Tab> buildTabs(NeiPanelState state, TextRenderer textRenderer) {
		List<Tab> result = new ArrayList<>();
		if (!showTabs) {
			return result;
		}
		int y = panelY + panelHeight - 15;
		int height = 13;
		int rightLimit = pageX - 4;
		int x = panelX + PADDING;

		x = appendTab(result, Text.translatable("nei.panel.nei").getString(), null, textRenderer, x, y, height, rightLimit);
		x = appendTab(result, Text.translatable("nei.panel.bookmarks").getString(), NeiPanelState.BOOKMARKS,
				textRenderer, x, y, height, rightLimit);

		try {
			for (dev.nei.item.ItemSubset subset : dev.nei.item.ItemSubsetList.get().subsets()) {
				if (subset == null || subset.name() == null || subset.name().isBlank()) {
					break;
				}
				int next = appendTab(result, subset.name(), subset.name(), textRenderer, x, y, height, rightLimit);
				if (next == x) {
					break;
				}
				x = next;
			}
		} catch (Throwable ignored) {
			// Subsets are optional; the panel must still render without them.
		}
		return result;
	}

	private static int appendTab(List<Tab> tabs, String label, String subset, TextRenderer textRenderer,
			int x, int y, int height, int rightLimit) {
		int width = width(textRenderer, label) + 6;
		if (label == null || label.isBlank() || x + width > rightLimit) {
			return x;
		}
		tabs.add(new Tab(label, subset, x, y, width, height));
		return x + width + 1;
	}

	public int slotX(int column) {
		return gridX + column * SLOT + 1;
	}

	public int slotY(int row) {
		return gridY + row * SLOT + 1;
	}

	/** Local grid index (0 .. columns*rows-1) under the mouse, or -1. */
	public int slotAt(double mouseX, double mouseY) {
		if (mouseX < gridX || mouseY < gridY) {
			return -1;
		}
		int column = (int) ((mouseX - gridX) / SLOT);
		int row = (int) ((mouseY - gridY) / SLOT);
		if (column < 0 || column >= columns || row < 0 || row >= rows) {
			return -1;
		}
		return row * columns + column;
	}

	public Tab tabAt(double mouseX, double mouseY) {
		for (Tab tab : tabs) {
			if (tab.contains(mouseX, mouseY)) {
				return tab;
			}
		}
		return null;
	}

	public boolean inside(double mouseX, double mouseY) {
		return mouseX >= panelX && mouseX < panelX + panelWidth
				&& mouseY >= panelY && mouseY < panelY + panelHeight;
	}

	public boolean searchInside(double mouseX, double mouseY) {
		return showSearch && mouseX >= searchX && mouseX < searchX + searchWidth
				&& mouseY >= searchY && mouseY < searchY + searchHeight;
	}

	public boolean helpInside(double mouseX, double mouseY) {
		return mouseX >= helpX && mouseX < helpX + HELP_SIZE
				&& mouseY >= helpY && mouseY < helpY + HELP_SIZE;
	}

	public int totalRows(int totalItems) {
		return totalItems <= 0 ? 0 : (totalItems + columns - 1) / columns;
	}

	public int maxScrollRow(int totalItems) {
		return Math.max(0, totalRows(totalItems) - rows);
	}

	public int firstItemIndex(NeiPanelState state) {
		return (state == null ? 0 : state.scrollRow()) * columns;
	}

	private static Bounds bounds(Screen screen) {
		if (screen instanceof HandledScreen<?> handled) {
			try {
				HandledScreenAccessor accessor = (HandledScreenAccessor) handled;
				return new Bounds(accessor.nei$getX(), accessor.nei$getY(),
						accessor.nei$getBackgroundWidth(), accessor.nei$getBackgroundHeight());
			} catch (Throwable ignored) {
				// The accessor mixin is not applied: fall back to the screen edge.
			}
		}
		return null;
	}

	private static TextRenderer textRenderer() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null ? null : client.textRenderer;
	}

	private static int width(TextRenderer textRenderer, String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return textRenderer == null ? text.length() * 6 : textRenderer.getWidth(text);
	}
}