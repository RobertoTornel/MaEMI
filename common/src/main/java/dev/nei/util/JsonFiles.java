package dev.nei.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.nei.Nei;

/** Small helpers for the JSON files NEI keeps next to the game config. */
public final class JsonFiles {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	public static <T> T read(Path path, Class<T> type, T fallback) {
		if (path == null || !Files.exists(path)) {
			return fallback;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			T value = GSON.fromJson(reader, type);
			return value == null ? fallback : value;
		} catch (IOException | JsonParseException e) {
			Nei.LOG.warn("[NEI] could not read {}; backing it up and starting fresh", path, e);
			try {
				Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// best effort
			}
			return fallback;
		}
	}

	public static void write(Path path, Object value) {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, writer);
			}
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Nei.LOG.error("[NEI] could not write {}", path, e);
		}
	}
}
