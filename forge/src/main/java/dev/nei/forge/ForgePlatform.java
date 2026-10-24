package dev.nei.forge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.nei.api.NeiPlugin;
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
	public List<NeiPlugin> plugins() {
		List<NeiPlugin> plugins = new ArrayList<>();
		org.objectweb.asm.Type annotationType = org.objectweb.asm.Type.getType(dev.nei.api.NeiPluginEntrypoint.class);
		for (net.minecraftforge.forgespi.language.ModFileScanData scan : ModList.get().getAllScanData()) {
			for (net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData data : scan.getAnnotations()) {
				if (data.annotationType().equals(annotationType)) {
					try {
						Class<?> clazz = Class.forName(data.memberName());
						if (NeiPlugin.class.isAssignableFrom(clazz)) {
							plugins.add((NeiPlugin) clazz.getDeclaredConstructor().newInstance());
						}
					} catch (Throwable t) {
						dev.nei.Nei.LOG.warn("[NEI] could not load plugin {}", data.memberName(), t);
					}
				}
			}
		}
		return plugins;
	}

	@Override
	public String modNameFor(String namespace) {
		return ModList.get().getModContainerById(namespace)
			.map(container -> container.getModInfo().getDisplayName())
			.orElse(namespace);
	}

	@Override
	public boolean matchesKeybind(String translationKey, int keyCode, int scanCode, int modifiers) {
		for (PendingBind bind : pending) {
			if (bind.translationKey.equals(translationKey) && bind.mapping != null && bind.mapping.matchesKey(keyCode, scanCode)) {
				return true;
			}
		}
		return false;
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
			return mapping != null && mapping.wasPressed();
		}

		@Override
		public boolean isPressed() {
			return mapping != null && mapping.isPressed();
		}
	}
}