package dev.nei.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.nei.Nei;
import dev.nei.compat.NeiCompat;
import dev.nei.integration.EmiPanelBridge;
import dev.nei.recipe.RecipeRegistry;

/**
 * EMI entrypoint. EMI owns recipe indexing; NEI contributes its plugins and compatibility modules
 * on top. {@code @EmiEntrypoint} is what Forge's scanner looks for; Fabric finds the class through
 * the {@code emi} entrypoint in fabric.mod.json.
 */
@EmiEntrypoint
public class NeiEmiPlugin implements EmiPlugin {
	@Override
	public void register(EmiRegistry registry) {
		Nei.LOG.info("[NEI] EMI registration started");
		try {
			RecipeRegistry.init(registry);
		} catch (Throwable t) {
			Nei.LOG.error("[NEI] recipe registration failed", t);
		}
		try {
			NeiCompat.install(registry);
		} catch (Throwable t) {
			Nei.LOG.error("[NEI] compatibility setup failed", t);
		}
		try {
			EmiPanelBridge.sync();
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not synchronise EMI sidebars", t);
		}
	}
}
