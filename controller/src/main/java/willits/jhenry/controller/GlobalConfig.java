package willits.jhenry.controller;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

public final class GlobalConfig {

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "global.json");

	private static int maxBlocks = 100;
	private static int soundVolume = 60;
	private static boolean autoTool = true;
	private static boolean peek = true;
	private static boolean pauseOnDamage = true;
	private static boolean loaded;

	private GlobalConfig() {
	}

	public static synchronized int maxBlocks() {
		ensureLoaded();
		return maxBlocks;
	}

	public static synchronized void setMaxBlocks(int value) {
		ensureLoaded();
		maxBlocks = clampMaxBlocks(value);
		save();
	}

	public static synchronized int soundVolume() {
		ensureLoaded();
		return soundVolume;
	}

	public static synchronized void setSoundVolume(int value) {
		ensureLoaded();
		soundVolume = Math.max(0, Math.min(100, value));
		save();
	}

	public static int clampMaxBlocks(int value) {
		return Math.max(50, Math.min(500, value));
	}

	public static synchronized boolean autoTool() {
		ensureLoaded();
		return autoTool;
	}

	public static synchronized void setAutoTool(boolean value) {
		ensureLoaded();
		autoTool = value;
		save();
	}

	public static synchronized boolean peek() {
		ensureLoaded();
		return peek;
	}

	public static synchronized void setPeek(boolean value) {
		ensureLoaded();
		peek = value;
		save();
	}

	public static synchronized boolean pauseOnDamage() {
		ensureLoaded();
		return pauseOnDamage;
	}

	public static synchronized void setPauseOnDamage(boolean value) {
		ensureLoaded();
		pauseOnDamage = value;
		save();
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Data data = load();
		if (data != null) {
			maxBlocks = clampMaxBlocks(data.maxBlocks);
			soundVolume = Math.max(0, Math.min(100, data.soundVolume));
			autoTool = data.autoTool == null ? true : data.autoTool;
			peek = data.peek == null ? true : data.peek;
			pauseOnDamage = data.pauseOnDamage == null ? true : data.pauseOnDamage;
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
			data.maxBlocks = maxBlocks;
			data.soundVolume = soundVolume;
			data.autoTool = autoTool;
			data.peek = peek;
			data.pauseOnDamage = pauseOnDamage;
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(data, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final class Data {
		int maxBlocks = 100;
		int soundVolume = 60;
		Boolean autoTool;
		Boolean peek;
		Boolean pauseOnDamage;
	}
}
