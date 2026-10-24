package dev.nei;

import java.util.EnumMap;
import java.util.Map;

import dev.nei.config.ConfigScreens;
import dev.nei.config.NeiConfig;
import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * NEI's client key bindings. The loader modules register the actual platform bindings and call
 * {@link #tick()} once per client tick.
 */
public final class NeiKeybinds {
	public enum Action {
		TOGGLE_PANEL("key.nei.toggle_panel", GLFW.GLFW_KEY_O),
		TOGGLE_BOOKMARKS("key.nei.toggle_bookmarks", GLFW.GLFW_KEY_B),
		AUTOCRAFT("key.nei.autocraft", GLFW.GLFW_KEY_C),
		OPEN_CONFIG("key.nei.open_config", GLFW.GLFW_KEY_P),
		HIDE_ITEM("key.nei.hide_item", GLFW.GLFW_KEY_H),
		TOGGLE_CHEAT("key.nei.toggle_cheat", GLFW.GLFW_KEY_UNKNOWN);

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
			if (action.defaultKey == GLFW.GLFW_KEY_UNKNOWN) {
				continue;
			}
			BINDS.put(action, platform.registerKeybind(action.translationKey, action.defaultKey));
		}
	}

	public static void tick() {
		if (!NeiConfig.get().general.enabled) {
			return;
		}
		for (Map.Entry<Action, NeiKeybind> entry : BINDS.entrySet()) {
			if (!entry.getValue().wasPressed()) {
				continue;
			}
			handle(entry.getKey());
		}
	}

	public static void handle(Action action) {
		switch (action) {
			case TOGGLE_PANEL -> {
				NeiConfigDataToggle.togglePanel();
			}
			case OPEN_CONFIG -> ConfigScreens.open();
			case TOGGLE_CHEAT -> {
				NeiConfig.get().cheat.enabled = !NeiConfig.get().cheat.enabled;
				NeiConfig.save();
			}
			default -> {
				// Implemented by the panel/autocraft subsystems once they are wired in.
			}
		}
	}

	/** Small indirection so the key handling never touches the config object twice. */
	private static final class NeiConfigDataToggle {
		private static void togglePanel() {
			NeiConfig.get().panel.enabled = !NeiConfig.get().panel.enabled;
			NeiConfig.save();
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				client.player.sendMessage(net.minecraft.text.Text.translatable(
					NeiConfig.get().panel.enabled ? "nei.status.panel_on" : "nei.status.panel_off"), true);
			}
		}
	}
}
