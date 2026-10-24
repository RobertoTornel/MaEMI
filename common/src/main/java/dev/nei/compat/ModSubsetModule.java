package dev.nei.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.util.Identifier;

/**
 * A compat module whose visible contribution is a subset named after the mod, containing every
 * stack exercised by its namespaces. Useful in large packs where scrolling the whole index is
 * hopeless, and entirely data-driven, so it cannot break the host mod.
 */
public class ModSubsetModule implements CompatModule {
	private final String id;
	private final String subsetName;
	private final List<String> requiredMods;
	private final Set<String> namespaces;
	private final Predicate<Identifier> extra;

	public ModSubsetModule(String id, String subsetName, List<String> requiredMods, Set<String> namespaces) {
		this(id, subsetName, requiredMods, namespaces, identifier -> true);
	}

	public ModSubsetModule(String id, String subsetName, List<String> requiredMods, Set<String> namespaces, Predicate<Identifier> extra) {
		this.id = id;
		this.subsetName = subsetName;
		this.requiredMods = List.copyOf(requiredMods);
		this.namespaces = Set.copyOf(namespaces);
		this.extra = extra;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public List<String> requiredMods() {
		return requiredMods;
	}

	@Override
	public List<SubsetPreset> subsetPresets() {
		List<SubsetPreset> presets = new ArrayList<>();
		presets.add(new SubsetPreset(subsetName, stack -> {
			Identifier identifier = stack.getId();
			return identifier != null && namespaces.contains(identifier.getNamespace()) && extra.test(identifier);
		}));
		return presets;
	}
}