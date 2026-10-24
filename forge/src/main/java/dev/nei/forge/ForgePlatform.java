package dev.nei.forge;

import java.nio.file.Path;

import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

public final class ForgePlatform implements NeiPlatform {
	@Override
	public String loaderName() {
		return "Forge";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return !net.minecraftforge.fml.loading.FMLEnvironment.production;
	}

	@Override
	public Path configDirectory() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public void sendCommand(String command) {
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
