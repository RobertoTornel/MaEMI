package dev.nei.panel;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.config.NeiConfigData;

/**
 * Mutable UI state for the NEI item panel. It lives as long as the client does, which is what lets
 * the search text and the selected subset follow the player between screens.
 */
public final class NeiPanelState {
	/** The virtual subset name used for the bookmark view. */
	public static final String BOOKMARKS = "Bookmarks";

	private int scrollRow;
	private String search = "";
	private String selectedSubset;
	private NeiConfigData.Side sideOverride;
	private boolean focused;
	private boolean enabled = true;
	private long lastSearchClick;
	private EmiStack hoveredStack = EmiStack.EMPTY;

	public EmiStack hoveredStack() {
		return hoveredStack;
	}

	public void setHoveredStack(EmiStack stack) {
		this.hoveredStack = stack == null ? EmiStack.EMPTY : stack;
	}

	public int scrollRow() {
		return scrollRow;
	}

	public void setScrollRow(int row) {
		this.scrollRow = Math.max(0, row);
	}

	public void scrollBy(int rows) {
		setScrollRow(scrollRow + rows);
	}

	/** The first visible row; the page is derived from it. */
	public int scroll() {
		return scrollRow;
	}

	public void setScroll(int row) {
		setScrollRow(row);
	}

	/** Zero-based page number for the given number of visible rows. */
	public int page(int rowsPerPage) {
		return scrollRow / Math.max(1, rowsPerPage);
	}

	public int getPage(int rowsPerPage) {
		return page(rowsPerPage);
	}

	public void setPage(int page, int rowsPerPage) {
		setScrollRow(Math.max(0, page) * Math.max(1, rowsPerPage));
	}

	public void resetPage() {
		scrollRow = 0;
	}

	public String search() {
		return search;
	}

	public void setSearch(String text) {
		String value = text == null ? "" : text;
		if (value.length() > 64) {
			value = value.substring(0, 64);
		}
		if (!search.equals(value)) {
			search = value;
			scrollRow = 0;
		}
	}

	public void appendSearch(char chr) {
		setSearch(search + chr);
	}

	public void backspaceSearch() {
		if (!search.isEmpty()) {
			setSearch(search.substring(0, search.length() - 1));
		}
	}

	public boolean isSearchFocused() {
		return focused;
	}

	public void setSearchFocused(boolean focused) {
		this.focused = focused;
	}

	public boolean isFocused() {
		return focused;
	}

	public void clearFocus() {
		focused = false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String selectedSubset() {
		return selectedSubset;
	}

	public void setSelectedSubset(String subset) {
		String value = subset == null || subset.isBlank() ? null : subset;
		if (!java.util.Objects.equals(selectedSubset, value)) {
			selectedSubset = value;
			scrollRow = 0;
		}
	}

	public boolean isAllSubset() {
		return selectedSubset == null;
	}

	public boolean isBookmarksSubset() {
		return BOOKMARKS.equals(selectedSubset);
	}

	public NeiConfigData.Side sideOverride() {
		return sideOverride;
	}

	public void setSideOverride(NeiConfigData.Side side) {
		this.sideOverride = side;
	}

	public void clearSideOverride() {
		this.sideOverride = null;
	}

	public NeiConfigData.Side effectiveSide(NeiConfigData.Side configured) {
		return sideOverride != null ? sideOverride : configured;
	}

	public long lastSearchClick() {
		return lastSearchClick;
	}

	public void markSearchClick(long time) {
		this.lastSearchClick = time;
	}

	/**
	 * Called when the open screen changes. The scroll position always resets; the query survives
	 * only when the config asks for it.
	 */
	public void onScreenChanged(boolean rememberSearch) {
		focused = false;
		scrollRow = 0;
		if (!rememberSearch) {
			search = "";
		}
	}

	public void reset() {
		focused = false;
		scrollRow = 0;
		search = "";
		selectedSubset = null;
		sideOverride = null;
	}
}