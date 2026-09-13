package willits.jhenry.controller;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;

public final class ScaffoldConfig {

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "scaffolds.json");

	private static final LinkedHashSet<String> ENABLED = new LinkedHashSet<>();
	private static boolean autoMine;
	private static boolean loaded;

	private ScaffoldConfig() {
	}

	public static synchronized List<String> enabled() {
		ensureLoaded();
		return new ArrayList<>(ENABLED);
	}

	public static synchronized void add(String id) {
		ensureLoaded();
		ENABLED.add(normalize(id));
		save();
	}

	public static synchronized void remove(String id) {
		ensureLoaded();
		ENABLED.remove(id);
		save();
	}

	public static synchronized boolean autoMine() {
		ensureLoaded();
		return autoMine;
	}

	public static synchronized void setAutoMine(boolean value) {
		ensureLoaded();
		autoMine = value;
		save();
	}

	public static String normalize(String input) {
		String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		if (text.isEmpty()) {
			return "";
		}
		return text.contains(":") ? text : "minecraft:" + text;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Data data = load();
		if (data != null) {
			autoMine = data.autoMine;
			if (data.blocks != null) {
				ENABLED.addAll(data.blocks);
			}
		}
	}

	private static Data load() {
		try {
			if (!Files.exists(FILE)) {
				return null;
			}
			try (Reader reader = Files.newBufferedReader(FILE)) {
				return GSON.fromJson(reader, Data.class);
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Data data = new Data();
			data.autoMine = autoMine;
			data.blocks = new ArrayList<>(ENABLED);
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final class Data {
		boolean autoMine;
		List<String> blocks;
	}
}
