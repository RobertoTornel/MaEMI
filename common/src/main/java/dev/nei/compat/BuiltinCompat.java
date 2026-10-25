package dev.nei.compat;

import java.util.List;
import java.util.Set;

/** The modules MaEMI ships with. Adding a mod here is the whole cost of a subset-style integration. */
final class BuiltinCompat {
	private BuiltinCompat() {
	}

	static void register(List<CompatModule> modules) {
		modules.add(new ModSubsetModule("gregtech", "GregTech", List.of("gtceu"), Set.of("gtceu")));
		modules.add(new ModSubsetModule("ae2", "Applied Energistics", List.of("ae2"), Set.of("ae2")));
		modules.add(new ModSubsetModule("create", "Create", List.of("create"), Set.of("create")));
		modules.add(new ModSubsetModule("mekanism", "Mekanism", List.of("mekanism"), Set.of("mekanism")));
		modules.add(new ModSubsetModule("thermal", "Thermal", List.of("thermal"), Set.of("thermal", "thermal_extra")));
		modules.add(new ModSubsetModule("immersiveengineering", "Immersive Engineering", List.of("immersiveengineering"), Set.of("immersiveengineering")));
		modules.add(new ModSubsetModule("botania", "Botania", List.of("botania"), Set.of("botania")));
		modules.add(new ModSubsetModule("ars_nouveau", "Ars Nouveau", List.of("ars_nouveau"), Set.of("ars_nouveau")));
		modules.add(new ModSubsetModule("tconstruct", "Tinkers' Construct", List.of("tconstruct"), Set.of("tconstruct")));
		modules.add(new ModSubsetModule("farmersdelight", "Farmer's Delight", List.of("farmersdelight"), Set.of("farmersdelight")));
		modules.add(new ModSubsetModule("modern_industrialization", "Modern Industrialization", List.of("modern_industrialization"), Set.of("modern_industrialization")));
		modules.add(new ModSubsetModule("powah", "Powah", List.of("powah"), Set.of("powah")));
		modules.add(new ModSubsetModule("ad_astra", "Ad Astra", List.of("ad_astra"), Set.of("ad_astra")));
	}
}
