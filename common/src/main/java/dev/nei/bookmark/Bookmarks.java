package dev.nei.bookmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;

public final class Bookmarks {
	private static final Bookmarks INSTANCE = new Bookmarks();

	private final List<BookmarkGroup> groups = new ArrayList<>();
	private BookmarkGroup active;

	private Bookmarks() {
		groups.add(new BookmarkGroup("Default"));
		active = groups.get(0);
	}

	public static Bookmarks get() {
		return INSTANCE;
	}

	public List<BookmarkGroup> groups() {
		return groups;
	}

	public BookmarkGroup active() {
		return active;
	}

	public void setActive(BookmarkGroup group) {
		this.active = group;
	}

	public void add(EmiStack stack) {
		if (stack != null && !stack.isEmpty()) {
			active.stacks().add(stack);
		}
	}

	public void load(Path configDirectory) {
	}

	public void save() {
	}
}
