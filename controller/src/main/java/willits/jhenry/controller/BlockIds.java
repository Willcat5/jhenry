package willits.jhenry.controller;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class BlockIds {

	private static final Gson GSON = new Gson();
	private static final Path FILE = Path.of(System.getProperty("user.home"), ".jhenry", "blocks.json");
	private static final String PREFIX = "minecraft:";

	private static final List<String> BASE = new ArrayList<>();
	private static final List<String> IDS = new ArrayList<>();
	private static boolean loaded;
	private static boolean fetching;
	private static boolean fetched;

	private BlockIds() {
	}

	public static synchronized boolean hasList() {
		ensureLoaded();
		return !IDS.isEmpty();
	}

	public static synchronized boolean isValid(String id) {
		ensureLoaded();
		if (IDS.isEmpty()) {
			return OreConfig.known().contains(id);
		}
		return Collections.binarySearch(IDS, id) >= 0;
	}

	public static synchronized List<String> matches(String query, int limit) {
		ensureLoaded();
		List<String> results = new ArrayList<>();
		if (query == null || query.isBlank()) {
			return results;
		}
		String lower = query.toLowerCase(Locale.ROOT);
		boolean namespaced = lower.contains(":");
		Set<String> enabled = new HashSet<>(OreConfig.enabled());
		for (String id : IDS) {
			if (enabled.contains(id)) {
				continue;
			}
			String display = id.startsWith(PREFIX) ? id.substring(PREFIX.length()) : id;
			String target = namespaced ? id : display;
			if (target.contains(lower)) {
				results.add(target);
				if (results.size() >= limit) {
					break;
				}
			}
		}
		return results;
	}

	public static synchronized void fetchAsync(List<BotConfig> bots) {
		ensureLoaded();
		if (fetched || fetching) {
			return;
		}
		fetching = true;
		List<BotConfig> candidates = new ArrayList<>(bots);
		new Thread(() -> {
			List<String> result = null;
			for (BotConfig bot : candidates) {
				result = new BotClient(bot).fetchBlocks();
				if (result != null && !result.isEmpty()) {
					break;
				}
			}
			synchronized (BlockIds.class) {
				if (result != null && !result.isEmpty()) {
					IDS.clear();
					IDS.addAll(BASE);
					IDS.addAll(result);
					dedupeSort();
					save();
					fetched = true;
				}
				fetching = false;
			}
		}, "jhenry-blocks").start();
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		BASE.addAll(bundled());
		IDS.addAll(BASE);
		List<String> cached = load();
		if (cached != null) {
			IDS.addAll(cached);
		}
		dedupeSort();
	}

	private static List<String> bundled() {
		List<String> list = new ArrayList<>();
		try (InputStream in = BlockIds.class.getResourceAsStream("/blocks.txt")) {
			if (in == null) {
				return list;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String trimmed = line.trim();
					if (!trimmed.isEmpty()) {
						list.add(trimmed);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	private static void dedupeSort() {
		List<String> unique = new ArrayList<>(new LinkedHashSet<>(IDS));
		Collections.sort(unique);
		IDS.clear();
		IDS.addAll(unique);
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
				GSON.toJson(new ArrayList<>(IDS), writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
