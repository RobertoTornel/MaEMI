package dev.nei.config;

import dev.nei.Nei;
import dev.nei.NeiClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Opens the settings screen, degrading gracefully when Cloth Config is not installed.
 */
public final class ConfigScreens {
	private ConfigScreens() {
	}

	public static Screen create(Screen parent) {
		boolean cloth = NeiClient.platform().isModLoaded("cloth-config") || NeiClient.platform().isModLoaded("cloth_config");
		if (cloth) {
			try {
				return NeiConfigScreen.create(parent);
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] Cloth Config screen failed to build", t);
			}
		}
		return new FallbackConfigScreen(parent);
	}

	public static void open() {
		MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(create(client.currentScreen));
	}
}
