package dev.nei.platform;

import java.nio.file.Path;
import java.util.List;

import dev.nei.api.NeiPlugin;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The small surface the common code needs from whichever loader we are running on.
 * Implementations live in the {@code fabric} and {@code forge} modules.
 */
public interface NeiPlatform {
	String loaderName();

	boolean isModLoaded(String modId);

	boolean isDevelopmentEnvironment();

	/** Directory holding config files (usually {@code config/}). */
	Path configDirectory();

	/** Sends a command as if the player typed it (without the leading slash). */
	void sendCommand(String command);

	/** Opens our config screen, returning false when no Cloth Config screen could be built. */
	boolean openConfigScreen(@Nullable Screen parent);

	/** Registers a client-side key binding and returns a handle whose {@code wasPressed} is polled each tick. */
	NeiKeybind registerKeybind(String translationKey, int defaultKey);

	/** Addon plugins discovered by the loader. */
	default List<NeiPlugin> plugins() {
		return List.of();
	}

	/** True when the given key event matches the binding registered for {@code translationKey}. */
	default boolean matchesKeybind(String translationKey, int keyCode, int scanCode, int modifiers) {
		return false;
	}

	/** Human-readable mod name for a registry namespace, or the namespace itself when unknown. */
	default String modNameFor(String namespace) {
		return namespace;
	}
}