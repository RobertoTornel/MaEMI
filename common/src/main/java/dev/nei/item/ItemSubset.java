package dev.nei.item;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.util.StackIO;

/**
 * A named collection of stacks, MaEMI's "item subset". The built-in subsets ({@code MaEMI} and
 * {@code Bookmarks}) are virtual and cannot be deleted.
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

	public boolean contains(EmiStack stack) {
		String key = StackIO.key(stack);
		for (EmiStack entry : stacks) {
			if (StackIO.key(entry).equals(key)) {
				return true;
			}
		}
		return false;
	}

	public boolean add(EmiStack stack) {
		if (stack == null || stack.isEmpty() || contains(stack)) {
			return false;
		}
		stacks.add(stack);
		dirty = true;
		return true;
	}

	public boolean remove(EmiStack stack) {
		String key = StackIO.key(stack);
		boolean removed = stacks.removeIf(entry -> StackIO.key(entry).equals(key));
		dirty |= removed;
		return removed;
	}

	public void clear() {
		if (!stacks.isEmpty()) {
			stacks.clear();
			dirty = true;
		}
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
