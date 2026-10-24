package dev.nei.fabric;

import java.nio.file.Path;

import dev.nei.Nei;
import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

public final class FabricPlatform implements NeiPlatform {
	@Override
	public String loaderName() {
		return "Fabric";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Path configDirectory() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public void sendCommand(String command) {
		Nei.LOG.debug("command: {}", command);
	}

	@Override
	public boolean openConfigScreen(@Nullable Screen parent) {
		return false;
	}

	@Override
	public NeiKeybind registerKeybind(String translationKey, int defaultKey) {
		return new NeiKeybind() {
			@Override
			public boolean wasPressed() {
				return false;
			}

			@Override
			public boolean isPressed() {
				return false;
			}
		};
	}
}
