package dev.nei.forge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.nei.config.ConfigScreens;
import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

public final class ForgePlatform implements NeiPlatform {
	private final List<PendingBind> pending = new ArrayList<>();

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
		return !FMLEnvironment.production;
	}

	@Override
	public Path configDirectory() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public void sendCommand(String command) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() != null) {
			client.getNetworkHandler().sendCommand(command);
		}
	}

	@Override
	public boolean openConfigScreen(@Nullable Screen parent) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(ConfigScreens.create(parent == null ? client.currentScreen : parent));
		return true;
	}

	@Override
	public NeiKeybind registerKeybind(String translationKey, int defaultKey) {
		PendingBind bind = new PendingBind(translationKey, defaultKey);
		pending.add(bind);
		return bind;
	}

	public List<PendingBind> pendingBinds() {
		return pending;
	}

	public static final class PendingBind implements NeiKeybind {
		private final String translationKey;
		private final int defaultKey;
		private KeyBinding mapping;

		PendingBind(String translationKey, int defaultKey) {
			this.translationKey = translationKey;
			this.defaultKey = defaultKey;
		}

		public KeyBinding create() {
			mapping = new KeyBinding(translationKey, InputUtil.Type.KEYSYM, defaultKey, "category.nei");
			return mapping;
		}

		@Override
		public boolean wasPressed() {
			return mapping != null && mapping.consumeClick();
		}

		@Override
		public boolean isPressed() {
			return mapping != null && mapping.isPressed();
		}
	}
}
