package dev.nei.api;

/**
 * Entry point for addons that want to contribute recipes, categories or GUI handlers to MaEMI.
 * Implementations are discovered from the loader (Fabric entrypoint {@code nei}, Forge
 * {@code @NeiPlugin}) or registered through {@link NeiPlugins#register}.
 */
public interface NeiPlugin {
	/** Mod id this plugin belongs to, used for logging and for the compat report. */
	String modId();

	void register(NeiRegistry registry);
}
