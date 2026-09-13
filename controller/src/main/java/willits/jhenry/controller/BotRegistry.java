package willits.jhenry.controller;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class BotRegistry {

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "bots.json");

	private BotRegistry() {
	}

	public static List<BotConfig> load() {
		try {
			if (!Files.exists(FILE)) {
				return new ArrayList<>();
			}
			try (Reader reader = Files.newBufferedReader(FILE)) {
				List<BotConfig> list = GSON.fromJson(reader, new TypeToken<List<BotConfig>>() {
				}.getType());
				return list != null ? list : new ArrayList<>();
			}
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	public static void save(List<BotConfig> bots) {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(bots, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
