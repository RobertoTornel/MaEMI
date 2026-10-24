package dev.nei.bookmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.NeiClient;
import dev.nei.util.JsonFiles;
import dev.nei.util.StackIO;

/**
 * NEI's bookmarks: named groups of stacks plus a bounded "recently looked at" history. The
 * bookmark panel is a view over this data; cheaper than EMI's favourites for NEI's use case because
 * it keeps the player's own grouping.
 */
public final class Bookmarks {
	private static final Bookmarks INSTANCE = new Bookmarks();
	private static final int MAX_HISTORY = 64;

	private final List<BookmarkGroup> groups = new ArrayList<>();
	private final List<EmiStack> history = new ArrayList<>();
	private BookmarkGroup active;
	private boolean loaded;

	private Bookmarks() {
	}

	public static Bookmarks get() {
		return INSTANCE;
	}

	public synchronized void load(Path configDirectory) {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = configDirectory == null ? null : configDirectory.resolve("nei-bookmarks.json");
		if (file == null) {
			return;
		}
		JsonObject root = JsonFiles.read(file, JsonObject.class, new JsonObject());
		groups.clear();
		history.clear();
		JsonArray groupsArray = root.has("groups") ? root.getAsJsonArray("groups") : new JsonArray();
		for (JsonElement element : groupsArray) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			BookmarkGroup group = new BookmarkGroup(object.has("name") ? object.get("name").getAsString() : "Default");
			if (object.has("stacks")) {
				for (JsonElement stackElement : object.getAsJsonArray("stacks")) {
					EmiStack stack = StackIO.read(stackElement);
					if (!stack.isEmpty()) {
						group.stacks().add(stack);
					}
				}
			}
			groups.add(group);
		}
		if (groups.isEmpty()) {
			groups.add(new BookmarkGroup("Default"));
		}
		active = groups.get(0);
		if (root.has("history")) {
			for (JsonElement element : root.getAsJsonArray("history")) {
				EmiStack stack = StackIO.read(element);
				if (!stack.isEmpty()) {
					history.add(stack);
				}
			}
		}
	}

	public synchronized void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray groupsArray = new JsonArray();
		for (BookmarkGroup group : groups) {
			JsonObject object = new JsonObject();
			object.addProperty("name", group.name());
			JsonArray stacks = new JsonArray();
			for (EmiStack stack : group.stacks()) {
				JsonElement element = StackIO.write(stack);
				if (element != null) {
					stacks.add(element);
				}
			}
			object.add("stacks", stacks);
			groupsArray.add(object);
		}
		root.add("groups", groupsArray);
		JsonArray historyArray = new JsonArray();
		for (EmiStack stack : history) {
			JsonElement element = StackIO.write(stack);
			if (element != null) {
				historyArray.add(element);
			}
		}
		root.add("history", historyArray);
		JsonFiles.write(file, root);
	}

	private Path file() {
		try {
			return NeiClient.configDirectory().resolve("nei-bookmarks.json");
		} catch (IllegalStateException e) {
			return null;
		}
	}

	public List<BookmarkGroup> groups() {
		ensureLoaded();
		return groups;
	}

	private void ensureLoaded() {
		if (!loaded) {
			try {
				load(NeiClient.configDirectory());
			} catch (IllegalStateException e) {
				loaded = true;
			}
		}
		if (groups.isEmpty()) {
			groups.add(new BookmarkGroup("Default"));
			active = groups.get(0);
		}
	}

	public BookmarkGroup active() {
		ensureLoaded();
		return active;
	}

	public void setActive(BookmarkGroup group) {
		ensureLoaded();
		if (group != null && groups.contains(group)) {
			active = group;
			save();
		}
	}

	public BookmarkGroup createGroup(String name) {
		ensureLoaded();
		BookmarkGroup group = new BookmarkGroup(name == null || name.isBlank() ? "Group" : name.trim());
		groups.add(group);
		save();
		return group;
	}

	public void deleteGroup(BookmarkGroup group) {
		ensureLoaded();
		if (groups.size() > 1 && groups.remove(group)) {
			if (active == group) {
				active = groups.get(0);
			}
			save();
		}
	}

	public void add(EmiStack stack) {
		ensureLoaded();
		if (stack == null || stack.isEmpty()) {
			return;
		}
		active().add(stack);
		for (int i = history.size() - 1; i >= 0; i--) {
			if (StackIO.key(history.get(i)).equals(StackIO.key(stack))) {
				history.remove(i);
			}
		}
		history.add(0, stack);
		while (history.size() > MAX_HISTORY) {
			history.remove(history.size() - 1);
		}
		save();
	}

	public void remove(EmiStack stack) {
		ensureLoaded();
		active().remove(stack);
		save();
	}

	public List<EmiStack> history() {
		ensureLoaded();
		return history;
	}
}
