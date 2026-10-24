package dev.nei.bookmark;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.util.StackIO;

public final class BookmarkGroup {
	private final String name;
	private final List<EmiStack> stacks = new ArrayList<>();

	public BookmarkGroup(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}

	public List<EmiStack> stacks() {
		return stacks;
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

	public void add(EmiStack stack) {
		if (stack != null && !stack.isEmpty() && !contains(stack)) {
			stacks.add(stack);
		}
	}

	public void remove(EmiStack stack) {
		String key = StackIO.key(stack);
		stacks.removeIf(entry -> StackIO.key(entry).equals(key));
	}
}
