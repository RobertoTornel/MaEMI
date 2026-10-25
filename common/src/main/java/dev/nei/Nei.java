package dev.nei;

import dev.nei.platform.NeiPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entry point. Both the Fabric and Forge initialisers call {@link #init(NeiPlatform)}
 * exactly once, before any client screen can open.
 */
public final class Nei {
	public static final String MOD_ID = "nei";
	public static final String MOD_NAME = "Not Enough Items";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	private static NeiPlatform platform;

	private Nei() {
	}

	public static void init(NeiPlatform plt) {
		if (platform != null) {
			throw new IllegalStateException("MaEMI already initialised");
		}
		platform = plt;
		LOG.info("[MaEMI] initialising on {} ({})", plt.loaderName(), plt.isDevelopmentEnvironment() ? "dev" : "production");
	}
}
