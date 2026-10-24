package dev.nei.item;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

/**
 * A named collection of stacks, NEI's "item subset". The special subsets created by the engine
 * ({@code ALL}, {@code BOOKMARKS}) cannot be deleted.
 */
public final class ItemSubset {
	private final String name;
	private final List<EmiStack> stacks = new ArrayList<>();
	private final boolean builtin;
	private boolean dirty;

	public ItemSubset(String name, boolean builtin) {
		this.name = name;
		this.builtin = builtin;
	}

	public String name() {
		return name;
	}

	public boolean builtin() {
		return builtin;
	}

	public List<EmiStack> stacks() {
		return stacks;
	}

	public boolean isEmpty() {
		return stacks.isEmpty();
	}

	public boolean dirty() {
		return dirty;
	}

	public void markDirty() {
		dirty = true;
	}

	public void clearDirty() {
		dirty = false;
	}
}
