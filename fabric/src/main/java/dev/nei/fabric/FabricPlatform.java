package dev.nei.fabric;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.nei.api.NeiPlugin;
import dev.nei.config.ConfigScreens;
import dev.nei.platform.NeiKeybind;
import dev.nei.platform.NeiPlatform;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.jetbrains.annotations.Nullable;

public final class FabricPlatform implements NeiPlatform {
	private final java.util.Map<String, KeyBinding> bindings = new java.util.HashMap<>();
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.getNetworkHandler() != null) {
			// 1.18.2 has no ClientPlayNetworkHandler#sendCommand; chat handles the leading slash.
			client.player.sendChatMessage("/" + command);
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
		for (var container : FabricLoader.getInstance().getEntrypointContainers("nei", NeiPlugin.class)) {
			plugins.add(container.getEntrypoint());
		}
		return plugins;
	}

	@Override
	public String modNameFor(String namespace) {
		return FabricLoader.getInstance().getModContainer(namespace)
			.map(container -> container.getMetadata().getName())
			.orElse(namespace);
	}

	@Override
	public boolean matchesKeybind(String translationKey, int keyCode, int scanCode, int modifiers) {
		KeyBinding binding = bindings.get(translationKey);
		return binding != null && binding.matchesKey(keyCode, scanCode);
	}

	@Override
	public NeiKeybind registerKeybind(String translationKey, int defaultKey) {
		KeyBinding binding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			translationKey, InputUtil.Type.KEYSYM, defaultKey, "category.nei"));
		bindings.put(translationKey, binding);
		return new NeiKeybind() {
			@Override
			public boolean wasPressed() {
				return binding.wasPressed();
			}

			@Override
			public boolean isPressed() {
				return binding.isPressed();
			}
		};
	}
}