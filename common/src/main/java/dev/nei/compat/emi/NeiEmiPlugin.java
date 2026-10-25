package dev.nei.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.nei.Nei;
import dev.nei.compat.NeiCompat;
import dev.nei.integration.EmiPanelBridge;
import dev.nei.recipe.RecipeRegistry;
import dev.nei.util.ToolIdentity;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

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
		installToolComparisons(registry);
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
			dev.nei.panel.NeiPanel.get().refresh();
		} catch (Throwable ignored) {
		}
		try {
			EmiPanelBridge.sync();
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not synchronise EMI sidebars", t);
		}
	}

	/**
	 * GT tools and other damageable items carry generated NBT (tool stats, damage). EMI's default
	 * comparison includes that NBT, so a hammer in the inventory does not equal the plain hammer
	 * ingredient a recipe asks for: the recipe looks uncraftable and recipe outputs do not match the
	 * hovered tool either. Ignoring NBT for damageable items makes tools behave like NEI tools:
	 * interchangeable, reused, and matched to their recipes.
	 */
	private static void installToolComparisons(EmiRegistry registry) {
		int count = 0;
		for (Item item : Registries.ITEM) {
			if (ToolIdentity.isToolItem(item)) {
				registry.setDefaultComparison(item, old -> Comparison.DEFAULT_COMPARISON);
				count++;
			}
		}
		Nei.LOG.info("[NEI] damageable items compared without NBT: {}", count);
	}
}