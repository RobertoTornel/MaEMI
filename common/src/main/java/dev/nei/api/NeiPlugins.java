package dev.nei.api;

import java.util.ArrayList;
import java.util.List;

import dev.nei.Nei;
import dev.nei.NeiClient;

/** Registry of addon plugins and the discovery bridge to the loader modules. */
public final class NeiPlugins {
	private static final List<NeiPlugin> PLUGINS = new ArrayList<>();

	private NeiPlugins() {
	}

	public static void register(NeiPlugin plugin) {
		if (plugin == null) {
			return;
		}
		for (NeiPlugin existing : PLUGINS) {
			if (existing.getClass() == plugin.getClass()) {
				return;
			}
		}
		PLUGINS.add(plugin);
	}

	public static List<NeiPlugin> all() {
		List<NeiPlugin> plugins = new ArrayList<>(PLUGINS);
		try {
			if (NeiClient.isInitialised()) {
				for (NeiPlugin plugin : NeiClient.platform().plugins()) {
					if (!plugins.contains(plugin)) {
						plugins.add(plugin);
					}
				}
			}
		} catch (Throwable t) {
			Nei.LOG.warn("[MaEMI] could not discover loader plugins", t);
		}
		return plugins;
	}
}
