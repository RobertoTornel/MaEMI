package dev.nei;

import java.util.EnumMap;
import java.util.Map;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.autocraft.AutoCraftService;
import dev.nei.config.ConfigScreens;
import dev.nei.dump.DumpService;
import dev.nei.item.HideList;
import dev.nei.panel.NeiPanel;
import dev.nei.panel.NeiPanelState;
import dev.nei.util.HoveredStack;
import dev.nei.config.NeiConfig;
import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * MaEMI's client key bindings. The loader modules register the actual platform bindings and call
 * {@link #tick()} once per client tick.
 */
public final class NeiKeybinds {
	public enum Action {
		// The panel/bookmark/hide features are retired and autocrafting is opt-in, so these
		// bindings are unbound by default. O, B, C and H belong to EMI again.
		TOGGLE_PANEL("key.nei.toggle_panel", GLFW.GLFW_KEY_UNKNOWN),
		TOGGLE_BOOKMARKS("key.nei.toggle_bookmarks", GLFW.GLFW_KEY_UNKNOWN),
		AUTOCRAFT("key.nei.autocraft", GLFW.GLFW_KEY_C),
		OPEN_CONFIG("key.nei.open_config", GLFW.GLFW_KEY_P),
		HIDE_ITEM("key.nei.hide_item", GLFW.GLFW_KEY_UNKNOWN),
		TOGGLE_CHEAT("key.nei.toggle_cheat", GLFW.GLFW_KEY_UNKNOWN),
		DUMP("key.nei.dump", GLFW.GLFW_KEY_F8);

		public final String translationKey;
		public final int defaultKey;

		Action(String translationKey, int defaultKey) {
			this.translationKey = translationKey;
			this.defaultKey = defaultKey;
		}
	}

	private static final Map<Action, NeiKeybind> BINDS = new EnumMap<>(Action.class);

	private NeiKeybinds() {
	}

	public static void registerAll(NeiPlatform platform) {
		for (Action action : Action.values()) {
			// Retired panel features are not registered at all, so they cannot steal keys.
			if (action == Action.TOGGLE_PANEL || action == Action.TOGGLE_BOOKMARKS || action == Action.HIDE_ITEM) {
				continue;
			}
			// Register even unbound actions: vanilla then shows them in Controls so the player can
			// assign a key. Skipping them is why Autocraft did not appear there.
			BINDS.put(action, platform.registerKeybind(action.translationKey, action.defaultKey));
		}
	}

	public static void tick() {
		// Crafting must keep running even if the panel was toggled off mid-plan.
		AutoCraftService.tick();
		if (!NeiConfig.get().general.enabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		for (Map.Entry<Action, NeiKeybind> entry : BINDS.entrySet()) {
			boolean pressed = entry.getValue().wasPressed();
			if (!pressed) {
				continue;
			}
			// While a screen is open, NeiHooks.keyPressed already handled the key; drop the queued
			// press so it does not fire again when the screen closes.
			if (client.currentScreen == null) {
				handle(entry.getKey());
			}
		}
	}

	/** Called from the screen hook, where vanilla key bindings do not fire. */
	public static boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (!NeiConfig.get().general.enabled) {
			return false;
		}
		for (Action action : Action.values()) {
			NeiKeybind bind = BINDS.get(action);
			if (bind == null) {
				continue;
			}
			if (NeiClient.platform().matchesKeybind(action.translationKey, keyCode, scanCode, modifiers)) {
				handle(action);
				return true;
			}
		}
		return false;
	}

	public static void handle(Action action) {
		switch (action) {
			case TOGGLE_PANEL -> {
				NeiConfigDataToggle.togglePanel();
			}
			case OPEN_CONFIG -> ConfigScreens.open();
			case DUMP -> DumpService.dumpAll();
			case AUTOCRAFT -> AutoCraftService.requestCraftHovered(autocraftAmount());
			case HIDE_ITEM -> {
				EmiStack stack = HoveredStack.get();
				if (!stack.isEmpty()) {
					boolean hidden = HideList.get().toggle(stack);
					NeiPanel.get().refresh();
					status(hidden ? "nei.status.hidden" : "nei.status.unhidden");
				}
			}
			case TOGGLE_BOOKMARKS -> {
				NeiPanelState state = NeiPanel.get().state();
				boolean showing = NeiPanelState.BOOKMARKS.equals(state.selectedSubset());
				state.setSelectedSubset(showing ? null : NeiPanelState.BOOKMARKS);
				NeiPanel.get().refresh();
			}
			case TOGGLE_CHEAT -> {
				NeiConfig.get().cheat.enabled = !NeiConfig.get().cheat.enabled;
				NeiConfig.save();
				status(NeiConfig.get().cheat.enabled ? "nei.status.cheat_on" : "nei.status.cheat_off");
			}
		}
	}

	/**
	 * C crafts one, Ctrl+C 32, Shift+C 64 and Ctrl+Shift+C as many as the stock allows. The modifier
	 * state is read when the bind fires, so it follows the keys actually held with the screen open.
	 */
	private static int autocraftAmount() {
		boolean control = Screen.hasControlDown();
		boolean shift = Screen.hasShiftDown();
		if (control && shift) {
			return 0;
		}
		if (control) {
			return 32;
		}
		if (shift) {
			return 64;
		}
		return 1;
	}

	private static void status(String key) {
		dev.nei.util.Messages.status(key);
	}

	/** Small indirection so the key handling never touches the config object twice. */
	private static final class NeiConfigDataToggle {
		private static void togglePanel() {
			NeiConfig.get().panel.enabled = !NeiConfig.get().panel.enabled;
			NeiConfig.save();
			dev.nei.integration.EmiPanelBridge.sync();
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				client.player.sendMessage(new net.minecraft.text.TranslatableText(
					NeiConfig.get().panel.enabled ? "nei.status.panel_on" : "nei.status.panel_off"), true);
			}
		}
	}
}