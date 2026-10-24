package dev.nei.bookmark;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

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
}
