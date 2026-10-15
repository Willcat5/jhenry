package willits.jhenry.controller;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class ModeConfig {

	public static final String TUNNEL = "TUNNEL";
	public static final String VOLUME = "VOLUME";

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "modes.json");

	private static Map<String, String> modes;
	private static boolean loaded;

	private ModeConfig() {
	}

	public static synchronized String get(BotConfig bot) {
		ensureLoaded();
		return modes.getOrDefault(key(bot), TUNNEL);
	}

	public static synchronized void set(BotConfig bot, String mode) {
		ensureLoaded();
		modes.put(key(bot), VOLUME.equals(mode) ? VOLUME : TUNNEL);
		save();
	}

	public static synchronized void remove(BotConfig bot) {
		ensureLoaded();
		if (modes.remove(key(bot)) != null) {
			save();
		}
	}

	private static String key(BotConfig bot) {
		return bot.host() + ":" + bot.port();
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		modes = load();
	}

	private static Map<String, String> load() {
		try {
			if (!Files.exists(FILE)) {
				return new HashMap<>();
			}
			try (Reader reader = Files.newBufferedReader(FILE)) {
				Map<String, String> map = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
				}.getType());
				return map != null ? map : new HashMap<>();
			}
		} catch (Exception e) {
			return new HashMap<>();
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(modes, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
