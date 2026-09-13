package willits.jhenry.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class ItemTextures {

	private static final String BASE =
			"https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@1.21.11/assets/";
	private static final String PREFIX = "minecraft:";

	private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, List<Consumer<BufferedImage>>> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, byte[]> BUNDLE = new HashMap<>();
	private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static boolean bundleLoaded;

	private ItemTextures() {
	}

	public static synchronized void get(String itemId, Consumer<BufferedImage> callback) {
		if (itemId == null || !itemId.startsWith(PREFIX)) {
			callback.accept(null);
			return;
		}
		BufferedImage cached = CACHE.get(itemId);
		if (cached != null) {
			callback.accept(cached == MISSING ? null : cached);
			return;
		}
		List<Consumer<BufferedImage>> pending = PENDING.get(itemId);
		if (pending != null) {
			pending.add(callback);
			return;
		}
		final List<Consumer<BufferedImage>> listeners = new CopyOnWriteArrayList<>();
		listeners.add(callback);
		PENDING.put(itemId, listeners);
		new Thread(() -> {
			BufferedImage image = fetch(itemId);
			CACHE.put(itemId, image == null ? MISSING : image);
			PENDING.remove(itemId);
			SwingUtilities.invokeLater(() -> {
				for (Consumer<BufferedImage> listener : listeners) {
					listener.accept(image);
				}
			});
		}, "jhenry-item").start();
	}

	private static BufferedImage fetch(String itemId) {
		String path = itemId.substring(PREFIX.length());
		BufferedImage image = fromBundle("assets/minecraft/textures/item/" + path + ".png");
		if (image == null) {
			image = fromBundle("assets/minecraft/textures/block/" + path + ".png");
		}
		if (image == null) {
			image = download("minecraft/textures/item/" + path + ".png");
		}
		if (image == null) {
			image = download("minecraft/textures/block/" + path + ".png");
		}
		return image;
	}

	private static BufferedImage fromBundle(String path) {
		loadBundle();
		byte[] bytes = BUNDLE.get(path);
		if (bytes == null) {
			return null;
		}
		try {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			return null;
		}
	}

	private static synchronized void loadBundle() {
		if (bundleLoaded) {
			return;
		}
		bundleLoaded = true;
		try (InputStream in = ItemTextures.class.getResourceAsStream("/minecraft-textures.zip")) {
			if (in == null) {
				return;
			}
			try (ZipInputStream zip = new ZipInputStream(in)) {
				ZipEntry entry;
				while ((entry = zip.getNextEntry()) != null) {
					if (!entry.isDirectory()) {
						BUNDLE.put(entry.getName(), zip.readAllBytes());
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static BufferedImage download(String assetPath) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(BASE + assetPath))
					.timeout(Duration.ofSeconds(8))
					.GET()
					.build();
			HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				return null;
			}
			return ImageIO.read(new ByteArrayInputStream(response.body()));
		} catch (Exception e) {
			return null;
		}
	}
}
