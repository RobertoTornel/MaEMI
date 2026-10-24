package dev.nei.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The on-disk shape of {@code config/nei.json}. Every field is public and defaulted so that Gson can
 * fill in missing values when a config from an older version is loaded.
 */
public final class NeiConfigData {
	public General general = new General();
	public Panel panel = new Panel();
	public Search search = new Search();
	public Autocraft autocraft = new Autocraft();
	public Cheat cheat = new Cheat();
	public Tooltip tooltip = new Tooltip();
	public Compat compat = new Compat();

	public static final class General {
		public boolean enabled = true;
		/** Show the NEI panel on every screen, not just container screens. */
		public boolean showEverywhere = false;
		/** Print a compatibility report to the log at startup. */
		public boolean logCompatReport = true;
		/** Play a click when an item is given. */
		public boolean clickSounds = true;
	}

	public static final class Panel {
		public boolean enabled = true;
		public Side side = Side.LEFT;
		public int columns = 9;
		public int rows = 8;
		public boolean showSearchBar = true;
		public boolean showItemCount = true;
		public boolean showItemIds = false;
		public boolean showHiddenItems = false;
		/** Show the page buttons and the subset tabs. */
		public boolean showTabs = true;
		/** Sort mode: NAME, MOD, ID, COUNT. */
		public Sort sort = Sort.MOD;
		/** Keep the search text between screens. */
		public boolean rememberSearch = true;
		/** Hide EMI's index sidebar on our side so the NEI panel can take its place. */
		public boolean replaceEmiIndex = false;
		public int tooltipLines = 0;
	}

	public enum Side {
		LEFT, RIGHT
	}

	public enum Sort {
		NAME, MOD, ID, COUNT
	}

	public static final class Search {
		public SearchMode mode = SearchMode.WILDCARD;
		/** Search tooltips for unprefixed terms as well (NEI only searches names by default). */
		public boolean searchTooltipsByDefault = false;
		/** Maximum number of search history entries. */
		public int historySize = 32;
	}

	public enum SearchMode {
		LITERAL, WILDCARD
	}

	public static final class Autocraft {
		public boolean enabled = true;
		/** How many levels of crafting recipes may be chained. */
		public int maxDepth = 12;
		/** Stop a single request after this many crafted items. */
		public int maxCrafts = 512;
		/** Use recipes that need a machine other than a crafting table. */
		public boolean allowOtherStations = true;
		/** Pull ingredients out of the player inventory automatically. */
		public boolean pullFromInventory = true;
		/** Show a toast when a plan cannot be completed. */
		public boolean reportMissing = true;
		/** Crafting tree depth used for the preview screen. */
		public int previewDepth = 6;
	}

	public static final class Cheat {
		public boolean enabled = true;
		/** Give a full stack on left click. */
		public boolean giveStacks = true;
		/** Delete the hovered stack with the trash button. */
		public boolean deleteItems = true;
		/** Allow the time/weather/gamemode buttons. */
		public boolean utilityButtons = true;
		/** Show the item ID in the tooltip. */
		public boolean showIds = false;
	}

	public static final class Tooltip {
		public boolean enabled = true;
		public boolean showModName = true;
		public boolean showRegistryName = false;
		public boolean showTags = false;
		public boolean showItemCount = true;
		public boolean showRecipeHint = true;
	}

	public static final class Compat {
		/** "auto", "on" or "off" per module id. */
		public Map<String, String> modules = new LinkedHashMap<>();
		/** Never load a module whose required mods are missing. */
		public boolean requireMods = true;
	}
}