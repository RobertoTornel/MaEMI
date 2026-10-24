package dev.nei;

import dev.nei.bookmark.Bookmarks;
import dev.nei.config.NeiConfig;
import dev.nei.item.ItemSubsetList;
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
		ItemSubsetList.get().load(plt.configDirectory());
		Bookmarks.get().load(plt.configDirectory());
		Nei.LOG.info("[NEI] loaded config from {}", NeiConfig.file());
	}

	public static NeiPlatform platform() {
		if (platform == null) {
			throw new IllegalStateException("NEI client not initialised");
		}
		return platform;
	}
}
