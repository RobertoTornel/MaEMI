package dev.nei.compat;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.NeiClient;
import dev.nei.item.ItemSubset;
import dev.nei.item.ItemSubsetList;
import dev.nei.recipe.RecipeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Compatibility manager. Every module is evaluated once per EMI reload against the mods that are
 * actually present; modules whose mods are missing are reported and never class-loaded.
 */
public final class NeiCompat {
	private static final List<CompatModule> MODULES = new ArrayList<>();
	private static final List<String> DISABLED = new ArrayList<>();
	private static boolean installed;
	private static boolean presetsCreated;

	static {
		BuiltinCompat.register(MODULES);
	}

	private NeiCompat() {
	}

	public static void install(EmiRegistry registry) {
		installed = true;
		DISABLED.clear();
		for (CompatModule module : MODULES) {
			String status = status(module);
			if (!isEnabled(module)) {
				DISABLED.add(module.id() + " (" + status + ")");
				continue;
			}
			try {
				module.install(registry);
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] compat module {} failed; disabling it", module.id(), t);
				DISABLED.add(module.id() + " (error)");
			}
		}
		if (!DISABLED.isEmpty()) {
			Nei.LOG.info("[MaEMI] compat modules disabled: {}", String.join(", ", DISABLED));
		}
	}

	public static void init(EmiRegistry registry) {
		install(registry);
	}

	public static List<CompatModule> plugins() {
		List<CompatModule> enabled = new ArrayList<>();
		for (CompatModule module : MODULES) {
			if (isEnabled(module)) {
				enabled.add(module);
			}
		}
		return enabled;
	}

	public static CompatReport report() {
		List<CompatReport.Entry> entries = new ArrayList<>();
		for (CompatModule module : MODULES) {
			entries.add(new CompatReport.Entry(module.id(), module.requiredMods(), isEnabled(module), status(module),
				module.subsetPresets().size()));
		}
		return new CompatReport(entries);
	}

	public static boolean isEnabled(CompatModule module) {
		try {
			if (!NeiClient.isInitialised()) {
				return false;
			}
			for (String mod : module.requiredMods()) {
				if (!NeiClient.platform().isModLoaded(mod)) {
					return false;
				}
			}
			for (String mod : module.conflictingMods()) {
				if (NeiClient.platform().isModLoaded(mod)) {
					return false;
				}
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	public static String status(CompatModule module) {
		List<String> missing = new ArrayList<>();
		try {
			if (!NeiClient.isInitialised()) {
				return "not initialised";
			}
			for (String mod : module.requiredMods()) {
				if (!NeiClient.platform().isModLoaded(mod)) {
					missing.add(mod);
				}
			}
			for (String mod : module.conflictingMods()) {
				if (NeiClient.platform().isModLoaded(mod)) {
					missing.add("conflict:" + mod);
				}
			}
		} catch (Throwable t) {
			return "error";
		}
		return missing.isEmpty() ? "enabled" : "missing " + String.join(", ", missing);
	}

	public static List<EmiRecipeCategory> workstationsFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return List.of();
		}
		return RecipeRegistry.categoriesForWorkstation(EmiStack.of(stack));
	}

	public static void appendTooltip(ItemStack stack, List<Text> lines) {
		for (CompatModule module : MODULES) {
			try {
				if (isEnabled(module)) {
					module.appendTooltip(stack, lines);
				}
			} catch (Throwable ignored) {
				// a tooltip must never break the game
			}
		}
	}

	/** Creates the preset subsets contributed by enabled modules, once the stack index exists. */
	public static void ensurePresets() {
		if (presetsCreated) {
			return;
		}
		// Copy off EMI's live index; it is rebuilt on EMI's reload thread.
		List<EmiStack> index = dev.nei.panel.ItemPanelSource.snapshotEmiIndex();
		if (index == null || index.isEmpty()) {
			return;
		}
		presetsCreated = true;
		for (CompatModule module : MODULES) {
			if (!isEnabled(module)) {
				continue;
			}
			for (SubsetPreset preset : module.subsetPresets()) {
				try {
					ItemSubset subset = ItemSubsetList.get().byName(preset.name());
					if (subset == null) {
						subset = ItemSubsetList.get().create(preset.name());
					} else if (!subset.isEmpty()) {
						continue;
					}
					List<EmiStack> matches = new ArrayList<>();
					for (EmiStack stack : index) {
						if (preset.filter().test(stack)) {
							matches.add(stack);
						}
					}
					ItemSubsetList.get().addAll(subset, matches);
					Nei.LOG.info("[MaEMI] compat subset '{}' created with {} stacks", preset.name(), matches.size());
				} catch (Throwable t) {
					Nei.LOG.warn("[MaEMI] could not create compat subset {}", preset.name(), t);
				}
			}
		}
	}

	public static boolean isInstalled() {
		return installed;
	}
}