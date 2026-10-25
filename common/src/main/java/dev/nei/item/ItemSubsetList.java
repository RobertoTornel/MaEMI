package dev.nei.item;

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
 * Owns the user-created item subsets and their persistence. {@code MaEMI} (everything) and
 * {@code Bookmarks} are handled by the panel itself and are therefore not stored here.
 */
public final class ItemSubsetList {
	public static final String ALL = "MaEMI";
	public static final String BOOKMARKS = "Bookmarks";

	private static final ItemSubsetList INSTANCE = new ItemSubsetList();

	private final List<ItemSubset> subsets = new ArrayList<>();
	private boolean loaded;

	private ItemSubsetList() {
	}

	public static ItemSubsetList get() {
		return INSTANCE;
	}

	public synchronized void load(Path configDirectory) {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = configDirectory == null ? null : configDirectory.resolve("nei-subsets.json");
		if (file == null) {
			return;
		}
		JsonObject root = JsonFiles.read(file, JsonObject.class, new JsonObject());
		subsets.clear();
		JsonArray array = root.has("subsets") ? root.getAsJsonArray("subsets") : new JsonArray();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			String name = object.has("name") ? object.get("name").getAsString() : "Subset";
			ItemSubset subset = new ItemSubset(name, false);
			if (object.has("stacks")) {
				for (JsonElement stackElement : object.getAsJsonArray("stacks")) {
					EmiStack stack = StackIO.read(stackElement);
					if (!stack.isEmpty()) {
						subset.stacks().add(stack);
					}
				}
			}
			subset.clearDirty();
			subsets.add(subset);
		}
	}

	public synchronized void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray array = new JsonArray();
		for (ItemSubset subset : subsets) {
			JsonObject object = new JsonObject();
			object.addProperty("name", subset.name());
			JsonArray stacks = new JsonArray();
			for (EmiStack stack : subset.stacks()) {
				JsonElement element = StackIO.write(stack);
				if (element != null) {
					stacks.add(element);
				}
			}
			object.add("stacks", stacks);
			array.add(object);
			subset.clearDirty();
		}
		root.add("subsets", array);
		JsonFiles.write(file, root);
	}

	public void saveIfDirty() {
		for (ItemSubset subset : subsets) {
			if (subset.dirty()) {
				save();
				return;
			}
		}
	}

	private Path file() {
		try {
			return NeiClient.configDirectory().resolve("nei-subsets.json");
		} catch (IllegalStateException e) {
			return null;
		}
	}

	public List<ItemSubset> subsets() {
		ensureLoaded();
		return subsets;
	}

	private void ensureLoaded() {
		if (!loaded) {
			try {
				load(NeiClient.configDirectory());
			} catch (IllegalStateException e) {
				loaded = true;
			}
		}
	}

	public ItemSubset byName(String name) {
		for (ItemSubset subset : subsets()) {
			if (subset.name().equals(name)) {
				return subset;
			}
		}
		return null;
	}

	public ItemSubset create(String name) {
		String unique = name == null || name.isBlank() ? "Subset" : name.trim();
		String candidate = unique;
		int i = 2;
		while (byName(candidate) != null) {
			candidate = unique + " " + i++;
		}
		ItemSubset subset = new ItemSubset(candidate, false);
		subsets().add(subset);
		save();
		return subset;
	}

	public synchronized ItemSubset rename(ItemSubset subset, String name) {
		if (subset == null || subset.builtin() || name == null) {
			return null;
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		List<ItemSubset> current = subsets();
		int index = current.indexOf(subset);
		if (index < 0) {
			return null;
		}
		if (trimmed.equals(subset.name())) {
			return subset;
		}
		if (isNameTaken(trimmed)) {
			return null;
		}
		ItemSubset replacement = new ItemSubset(trimmed, false);
		replacement.stacks().addAll(subset.stacks());
		current.set(index, replacement);
		save();
		return replacement;
	}

	/** True when a stored subset or one of the virtual tabs already uses this name. */
	public synchronized boolean isNameTaken(String name) {
		if (name == null) {
			return false;
		}
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		if (ALL.equals(trimmed) || BOOKMARKS.equals(trimmed)) {
			return true;
		}
		return byName(trimmed) != null;
	}

	public synchronized boolean delete(ItemSubset subset) {
		if (subset == null || subset.builtin() || !subsets().remove(subset)) {
			return false;
		}
		save();
		return true;
	}

	public void addToSubset(ItemSubset subset, EmiStack stack) {
		if (subset != null && subset.add(stack)) {
			save();
		}
	}

	public void removeFromSubset(ItemSubset subset, EmiStack stack) {
		if (subset != null && subset.remove(stack)) {
			save();
		}
	}

	public void addAll(ItemSubset subset, List<EmiStack> stacks) {
		if (subset == null) {
			return;
		}
		boolean changed = false;
		for (EmiStack stack : stacks) {
			changed |= subset.add(stack);
		}
		if (changed) {
			save();
		}
	}
}
