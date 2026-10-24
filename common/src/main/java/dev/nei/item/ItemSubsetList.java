package dev.nei.item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

/**
 * Owns the item subsets and their persistence. Implemented by the item subsystem; this contract is
 * kept deliberately small so the panel only sees what it needs.
 */
public final class ItemSubsetList {
	public static final String ALL = "NEI";
	public static final String BOOKMARKS = "Bookmarks";

	private static final ItemSubsetList INSTANCE = new ItemSubsetList();

	private final List<ItemSubset> subsets = new ArrayList<>();

	private ItemSubsetList() {
		subsets.add(new ItemSubset(ALL, true));
		subsets.add(new ItemSubset(BOOKMARKS, true));
	}

	public static ItemSubsetList get() {
		return INSTANCE;
	}

	public List<ItemSubset> subsets() {
		return subsets;
	}

	public ItemSubset byName(String name) {
		for (ItemSubset subset : subsets) {
			if (subset.name().equals(name)) {
				return subset;
			}
		}
		return null;
	}

	public ItemSubset create(String name) {
		ItemSubset subset = new ItemSubset(name, false);
		subsets.add(subset);
		return subset;
	}

	public void delete(ItemSubset subset) {
		if (!subset.builtin()) {
			subsets.remove(subset);
		}
	}

	public void addToSubset(ItemSubset subset, EmiStack stack) {
		subset.stacks().add(stack);
		subset.markDirty();
	}

	public void load(Path configDirectory) {
	}

	public void save() {
	}
}
