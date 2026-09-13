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
import com.google.gson.reflect.TypeToken;

public final class OreConfig {

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "ores.json");
	private static final List<String> DEFAULTS = List.of(
			"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
			"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
			"minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
			"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
			"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
			"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
			"minecraft:ancient_debris", "minecraft:nether_quartz_ore");

	private static final LinkedHashSet<String> ENABLED = new LinkedHashSet<>();
	private static boolean loaded;

	private OreConfig() {
	}

	public static synchronized List<String> enabled() {
		ensureLoaded();
		return new ArrayList<>(ENABLED);
	}

	public static synchronized List<String> known() {
		ensureLoaded();
		LinkedHashSet<String> ids = new LinkedHashSet<>(DEFAULTS);
		ids.addAll(ENABLED);
		return new ArrayList<>(ids);
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

	public static synchronized void reset() {
		ensureLoaded();
		ENABLED.clear();
		ENABLED.addAll(DEFAULTS);
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
		List<String> list = load();
		if (list == null) {
			ENABLED.addAll(DEFAULTS);
		} else {
			ENABLED.addAll(list);
		}
	}

	private static List<String> load() {
		try {
			if (!Files.exists(FILE)) {
				return null;
			}
			try (Reader reader = Files.newBufferedReader(FILE)) {
				return GSON.fromJson(reader, new TypeToken<List<String>>() {
				}.getType());
			}
		} catch (Exception e) {
			return null;
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(new ArrayList<>(ENABLED), writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
