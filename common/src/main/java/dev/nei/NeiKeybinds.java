package dev.nei;

import java.util.EnumMap;
import java.util.Map;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.autocraft.AutoCraftService;
import dev.nei.config.ConfigScreens;
import dev.nei.dump.DumpService;
import dev.nei.item.HideList;
import dev.nei.util.HoveredStack;
import dev.nei.config.NeiConfig;
import dev.nei.overlay.CalcWindow;
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
		// The panel is gone, and the bookmark view lived inside it. The bookmark data class survives
		// for its file format and compat presets, but there is no view to toggle, so no binding claims
		// to. Autocraft is the only action bound by default; O, B and H belong to EMI again.
		AUTOCRAFT("key.nei.autocraft", GLFW.GLFW_KEY_C),
		OPEN_CONFIG("key.nei.open_config", GLFW.GLFW_KEY_P),
		HIDE_ITEM("key.nei.hide_item", GLFW.GLFW_KEY_UNKNOWN),
		TOGGLE_CHEAT("key.nei.toggle_cheat", GLFW.GLFW_KEY_UNKNOWN),
		DUMP("key.nei.dump", GLFW.GLFW_KEY_F8),
		CLOSE_CALCULATOR("key.nei.calc_close", GLFW.GLFW_KEY_UNKNOWN);

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
			// Register even unbound actions: vanilla then shows them in Controls so the player can
			// assign a key. Skipping them is why Autocraft did not appear there.
			BINDS.put(action, platform.registerKeybind(action.translationKey, action.defaultKey));
		}
	}

	public static void tick() {
		// Crafting must keep running even if MaEMI's general switch is off mid-plan.
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
			case OPEN_CONFIG -> ConfigScreens.open();
			case DUMP -> DumpService.dumpAll();
			case AUTOCRAFT -> AutoCraftService.requestCraftHovered(autocraftAmount());
			case HIDE_ITEM -> {
				// EMI has no hide list of its own, so this only persists the player's choice; the
				// message says exactly that instead of implying EMI's index changed.
				EmiStack stack = HoveredStack.get();
				if (!stack.isEmpty()) {
					boolean hidden = HideList.get().toggle(stack);
					status(hidden ? "nei.status.hidden" : "nei.status.unhidden");
				}
			}
			case CLOSE_CALCULATOR -> CalcWindow.get().close();
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

}