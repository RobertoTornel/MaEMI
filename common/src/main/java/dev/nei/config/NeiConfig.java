package dev.nei.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.nei.Nei;

public final class NeiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Path path;
	private static NeiConfigData data = new NeiConfigData();

	private NeiConfig() {
	}

	public static void load(Path configDirectory) {
		path = configDirectory.resolve("nei.json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				NeiConfigData loaded = GSON.fromJson(reader, NeiConfigData.class);
				if (loaded != null) {
					data = sanitise(loaded);
				}
			} catch (IOException | JsonParseException e) {
				Nei.LOG.error("[MaEMI] could not read {}; using defaults", path, e);
				data = new NeiConfigData();
			}
		}
		save();
	}

	private static NeiConfigData sanitise(NeiConfigData loaded) {
		if (loaded.general == null) loaded.general = new NeiConfigData.General();
		if (loaded.panel == null) loaded.panel = new NeiConfigData.Panel();
		if (loaded.search == null) loaded.search = new NeiConfigData.Search();
		if (loaded.autocraft == null) loaded.autocraft = new NeiConfigData.Autocraft();
		if (loaded.cheat == null) loaded.cheat = new NeiConfigData.Cheat();
		if (loaded.tooltip == null) loaded.tooltip = new NeiConfigData.Tooltip();
		if (loaded.compat == null) loaded.compat = new NeiConfigData.Compat();
		if (loaded.compat.modules == null) loaded.compat.modules = new java.util.LinkedHashMap<>();
		loaded.panel.columns = Math.max(4, Math.min(12, loaded.panel.columns));
		loaded.panel.rows = Math.max(2, Math.min(20, loaded.panel.rows));
		loaded.autocraft.maxDepth = Math.max(1, Math.min(64, loaded.autocraft.maxDepth));
		loaded.autocraft.maxCrafts = Math.max(1, Math.min(4096, loaded.autocraft.maxCrafts));
		return loaded;
	}

	public static NeiConfigData get() {
		return data;
	}

	public static void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			Nei.LOG.error("[MaEMI] could not write {}", path, e);
		}
	}

	public static Path file() {
		return path;
	}

	public static void reset() {
		data = new NeiConfigData();
		save();
	}
}
