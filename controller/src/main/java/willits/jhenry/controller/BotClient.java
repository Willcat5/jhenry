package willits.jhenry.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public final class BotClient {

	private static final Gson GSON = new Gson();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	private final BotConfig config;

	public BotClient(BotConfig config) {
		this.config = config;
	}

	public BotStatus fetchStatus() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/status"))
					.timeout(Duration.ofSeconds(2))
					.header("X-JHenry-Token", config.token())
					.GET()
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return null;
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!json.has("online")) {
				return null;
			}
			return GSON.fromJson(json, BotStatus.class);
		} catch (Exception e) {
			return isConnectionRefused(e) ? BotStatus.offline() : null;
		}
	}

	private static boolean isConnectionRefused(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof ConnectException) {
				return true;
			}
		}
		return false;
	}

	public boolean sendCommand(String action) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("action", action);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/command"))
					.timeout(Duration.ofSeconds(3))
					.header("X-JHenry-Token", config.token())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean sendFilter(List<String> ores) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("action", "filter");
			JsonArray array = new JsonArray();
			for (String ore : ores) {
				array.add(ore);
			}
			body.add("ores", array);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/command"))
					.timeout(Duration.ofSeconds(3))
					.header("X-JHenry-Token", config.token())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean sendSettings(boolean autoMine, List<String> scaffolds) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("action", "settings");
			body.addProperty("autoMine", autoMine);
			JsonArray array = new JsonArray();
			for (String id : scaffolds) {
				array.add(id);
			}
			body.add("scaffolds", array);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/command"))
					.timeout(Duration.ofSeconds(3))
					.header("X-JHenry-Token", config.token())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	private String base() {
		return "http://" + config.host() + ":" + config.port();
	}

	public List<String> fetchBlocks() {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/blocks"))
					.timeout(Duration.ofSeconds(10))
					.header("X-JHenry-Token", config.token())
					.GET()
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return null;
			}
			return GSON.fromJson(response.body(), new TypeToken<List<String>>() {
			}.getType());
		} catch (Exception e) {
			return null;
		}
	}

	public BufferedImage fetchMap() {		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(base() + "/map"))
					.timeout(Duration.ofSeconds(3))
					.header("X-JHenry-Token", config.token())
					.GET()
					.build();
			HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				return null;
			}
			return ImageIO.read(new ByteArrayInputStream(response.body()));
		} catch (Exception e) {
			return null;
		}
	}
}
