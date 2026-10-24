package dev.nei.compat;

import java.util.List;
import java.util.function.Predicate;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * One optional integration. A module is only loaded when every mod id it requires is present; this
 * is how NEI stays useful in a small pack and still adapts to a kitchen-sink pack like Monifactory.
 */
public interface CompatModule {
	String id();

	List<String> requiredMods();

	/** Mods that make this module pointless or dangerous; usually a competing addon. */
	default List<String> conflictingMods() {
		return List.of();
	}

	default void install(EmiRegistry registry) {
	}

	/** Subsets this module contributes, created lazily once EMI's index exists. */
	default List<SubsetPreset> subsetPresets() {
		return List.of();
	}

	/** Extra tooltip lines for a stack, if this module has something to say about it. */
	default void appendTooltip(ItemStack stack, List<Text> lines) {
	}

	default boolean isEnabled() {
		return NeiCompat.isEnabled(this);
	}

	default String status() {
		return NeiCompat.status(this);
	}
}
