package dev.nei;

import java.nio.file.Path;

import dev.nei.config.NeiConfig;
import dev.nei.integration.EmiPanelBridge;
import dev.nei.platform.NeiPlatform;

/** Client-side singleton state, kept separate from {@link Nei} so a dedicated server never loads GUI classes. */
public final class NeiClient {
	private static NeiPlatform platform;
	private static boolean initialised;

	private NeiClient() {
	}

	public static void init(NeiPlatform plt) {
		if (initialised) {
			return;
		}
		initialised = true;
		platform = plt;
		NeiConfig.load(plt.configDirectory());
		Nei.LOG.info("[NEI] loaded config from {}", NeiConfig.file());
		EmiPanelBridge.sync();
	}

	public static NeiPlatform platform() {
		if (platform == null) {
			throw new IllegalStateException("NEI client not initialised");
		}
		return platform;
	}

	public static boolean isInitialised() {
		return platform != null;
	}

	/** Directory holding the mod's JSON data files; available after {@link #init}. */
	public static Path configDirectory() {
		return platform().configDirectory();
	}
}
