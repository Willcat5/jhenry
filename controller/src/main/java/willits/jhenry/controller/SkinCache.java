package willits.jhenry.controller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class SkinCache {

	private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final Gson GSON = new Gson();
	private static final BufferedImage PLACEHOLDER = createPlaceholder();

	private SkinCache() {
	}

	public static BufferedImage placeholder() {
		return PLACEHOLDER;
	}

	public static void get(String uuid, Consumer<BufferedImage> callback) {
		if (uuid == null || uuid.isEmpty()) {
			callback.accept(PLACEHOLDER);
			return;
		}
		BufferedImage cached = CACHE.get(uuid);
		if (cached != null) {
			callback.accept(cached);
			return;
		}
		if (!PENDING.add(uuid)) {
			return;
		}
		new Thread(() -> {
			BufferedImage image = fetch(uuid);
			CACHE.put(uuid, image);
			PENDING.remove(uuid);
			SwingUtilities.invokeLater(() -> callback.accept(image));
		}, "jhenry-skin").start();
	}

	private static BufferedImage fetch(String uuid) {
		try {
			String id = uuid.replace("-", "");
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id))
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return PLACEHOLDER;
			}
			JsonObject profile = GSON.fromJson(response.body(), JsonObject.class);
			JsonArray properties = profile.getAsJsonArray("properties");
			for (JsonElement element : properties) {
				JsonObject property = element.getAsJsonObject();
				if ("textures".equals(property.get("name").getAsString())) {
					String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()),
							StandardCharsets.UTF_8);
					JsonObject skin = GSON.fromJson(decoded, JsonObject.class)
							.getAsJsonObject("textures")
							.getAsJsonObject("SKIN");
					return downloadHead(skin.get("url").getAsString());
				}
			}
		} catch (Exception e) {
			// fall through to placeholder
		}
		return PLACEHOLDER;
	}

	private static BufferedImage downloadHead(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();
		byte[] bytes = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
		BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
		if (skin == null) {
			return PLACEHOLDER;
		}

		BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = head.createGraphics();
		graphics.drawImage(skin, 0, 0, 8, 8, 8, 8, 16, 16, null);
		graphics.drawImage(skin, 0, 0, 8, 8, 40, 8, 48, 16, null);
		graphics.dispose();
		return head;
	}

	private static BufferedImage createPlaceholder() {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x6B4A2B));
		g.fillRect(0, 0, 8, 8);
		g.setColor(new Color(0xE0AC69));
		g.fillRect(0, 3, 8, 5);
		g.setColor(Color.WHITE);
		g.fillRect(1, 4, 2, 1);
		g.fillRect(5, 4, 2, 1);
		g.setColor(new Color(0x2E2E8F));
		g.fillRect(2, 4, 1, 1);
		g.fillRect(6, 4, 1, 1);
		g.setColor(new Color(0x9E6B4A));
		g.fillRect(3, 6, 2, 1);
		g.dispose();
		return image;
	}
}
