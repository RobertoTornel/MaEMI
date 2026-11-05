package dev.nei.autocraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import dev.nei.Nei;

/**
 * Remembers which recipe was used to make an item, so the next plan for that item starts from the
 * same recipe instead of searching again.
 *
 * <p>Planning is a search, and a search can land on a different recipe each time: the same item may
 * be reachable through a machine, through a crafting table, or through a chain of intermediates, and
 * the ordering heuristics only approximate which is best. When a plan succeeds, the recipe that
 * actually worked is worth keeping. The next request for that item tries it first, which makes
 * repeated crafts of the same thing fast and, more importantly, consistent -- the mod stops
 * changing its mind about how to make a plate.</p>
 *
 * <p>The cache is a hint, never a rule: a remembered recipe is only used if it still produces the
 * item and can still be filled in the open screen. Anything else falls back to the normal search.</p>
 */
public final class CraftCache {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int MAX_ENTRIES = 4096;

	private static final Map<String, String> RECIPES = new HashMap<>();
	private static Path file;
	private static boolean dirty;

	private CraftCache() {
	}

	public static synchronized void load(Path configDirectory) {
		if (configDirectory == null) {
			return;
		}
		file = configDirectory.resolve("nei-craft-cache.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, String> loaded = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
			}.getType());
			if (loaded != null) {
				RECIPES.clear();
				RECIPES.putAll(loaded);
			}
		} catch (IOException | JsonParseException e) {
			Nei.LOG.warn("[MaEMI] could not read {}; starting with an empty craft cache", file, e);
		}
	}

	/** The recipe last used to make {@code itemKey}, or {@code null} if none is remembered. */
	public static synchronized String recipeFor(String itemKey) {
		return itemKey == null ? null : RECIPES.get(itemKey);
	}

	/** Records that {@code recipeKey} made {@code itemKey}. */
	public static synchronized void remember(String itemKey, String recipeKey) {
		if (itemKey == null || itemKey.isEmpty() || recipeKey == null || recipeKey.isEmpty()) {
			return;
		}
		if (RECIPES.size() >= MAX_ENTRIES && !RECIPES.containsKey(itemKey)) {
			// A cache that grows without bound is a leak; the oldest entries are the least useful.
			RECIPES.clear();
		}
		String previous = RECIPES.put(itemKey, recipeKey);
		if (!recipeKey.equals(previous)) {
			dirty = true;
		}
	}

	public static synchronized void save() {
		if (!dirty || file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(RECIPES, writer);
			}
			dirty = false;
		} catch (IOException e) {
			Nei.LOG.warn("[MaEMI] could not write {}", file, e);
		}
	}

	public static synchronized int size() {
		return RECIPES.size();
	}
}
