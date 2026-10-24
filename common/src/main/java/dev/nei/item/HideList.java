package dev.nei.item;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.NeiClient;
import dev.nei.util.JsonFiles;
import dev.nei.util.StackIO;

/**
 * Stacks the player asked NEI never to show again, mirroring NEI's hidden items list. Keys are
 * registry id plus NBT so a renamed item does not hide the plain one.
 */
public final class HideList {
	private static final HideList INSTANCE = new HideList();

	private final Set<String> hidden = new LinkedHashSet<>();
	private boolean loaded;

	private HideList() {
	}

	public static HideList get() {
		return INSTANCE;
	}

	public synchronized void load(Path configDirectory) {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = configDirectory == null ? null : configDirectory.resolve("nei-hidden.json");
		if (file == null) {
			return;
		}
		JsonObject root = JsonFiles.read(file, JsonObject.class, new JsonObject());
		hidden.clear();
		if (root.has("hidden")) {
			for (JsonElement element : root.getAsJsonArray("hidden")) {
				if (element.isJsonPrimitive()) {
					hidden.add(element.getAsString());
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
		JsonArray array = new JsonArray();
		for (String key : hidden) {
			array.add(key);
		}
		root.add("hidden", array);
		JsonFiles.write(file, root);
	}

	private Path file() {
		try {
			return NeiClient.configDirectory().resolve("nei-hidden.json");
		} catch (IllegalStateException e) {
			return null;
		}
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

	public boolean isHidden(EmiStack stack) {
		ensureLoaded();
		return hidden.contains(StackIO.key(stack));
	}

	public boolean hide(EmiStack stack) {
		ensureLoaded();
		boolean changed = hidden.add(StackIO.key(stack));
		if (changed) {
			save();
		}
		return changed;
	}

	public boolean unhide(EmiStack stack) {
		ensureLoaded();
		boolean changed = hidden.remove(StackIO.key(stack));
		if (changed) {
			save();
		}
		return changed;
	}

	public boolean toggle(EmiStack stack) {
		return isHidden(stack) ? unhide(stack) : hide(stack);
	}

	public int size() {
		ensureLoaded();
		return hidden.size();
	}

	public void clear() {
		ensureLoaded();
		hidden.clear();
		save();
	}
}
