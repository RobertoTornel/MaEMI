package dev.nei.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The on-disk shape of {@code config/nei.json}. Every field is public and defaulted so that Gson can
 * fill in missing values when a config from an older version is loaded.
 */
public final class NeiConfigData {
	public General general = new General();
	public Search search = new Search();
	public Calculator calculator = new Calculator();
	public Autocraft autocraft = new Autocraft();
	public Cheat cheat = new Cheat();
	public Tooltip tooltip = new Tooltip();
	public Compat compat = new Compat();

	public static final class General {
		public boolean enabled = true;
		/** Print a compatibility report to the log at startup. */
		public boolean logCompatReport = true;
		/** Play a click when an item is given. */
		public boolean clickSounds = true;
	}

	/**
	 * The floating calculator window introduced in 1.0.4. Everything here is restored on startup and
	 * written back when the window is moved, renamed or closed, so a player keeps their layout.
	 *
	 * <p>The panel section this replaces was deleted with the custom item panel; old config files that
	 * still contain a {@code panel} object load fine because unknown keys are ignored.</p>
	 */
	public static final class Calculator {
		/** Window origin in GUI pixels; negative means "not placed yet", so the default corner is used. */
		public int x = -1;
		public int y = -1;
		public int width = 136;
		public int height = 46;
		/** Custom name shown in the title bar; empty falls back to the translated default. */
		public String name = "";
		/** Whether the window was open when the game last closed. */
		public boolean open = true;
		/** Draw the evaluated result at the right end of EMI's search bar. */
		public boolean showInSearchBar = true;
		/** Whether the last calculation is pinned and persists until explicitly closed. */
		public boolean pinned = false;
		/** Whether to show the expression beneath the value in the window. */
		public boolean showExpression = true;
	}

	public static final class Search {
		public SearchMode mode = SearchMode.WILDCARD;
		/** Search tooltips for unprefixed terms as well (MaEMI only searches names by default). */
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
		public boolean showNbt = false;
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