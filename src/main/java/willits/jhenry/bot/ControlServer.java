package willits.jhenry.bot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import willits.jhenry.JHenry;
import willits.jhenry.client.BotActions;
import willits.jhenry.client.Persistence;
import willits.jhenry.client.dig.TunnelDigger;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.OreFilter;
import willits.jhenry.mapping.PlannedCell;
import willits.jhenry.mapping.ScaffoldFilter;
import willits.jhenry.mapping.Segment;
import willits.jhenry.mapping.TunnelPlan;

public final class ControlServer {

	public static final int DEFAULT_PORT = 8765;
	public static final int PORT_RANGE = 50;
	public static final String DEFAULT_TOKEN = "jhenry";

	private static HttpServer server;
	private static ExecutorService executor;
	private static String token = DEFAULT_TOKEN;
	private static int port = DEFAULT_PORT;
	private static String blockListJson;

	private ControlServer() {
	}

	public static int derivePort(String account) {
		return DEFAULT_PORT + Math.floorMod(account.hashCode(), PORT_RANGE);
	}

	public static void start(int requestedPort, String requestedToken) {
		token = requestedToken;
		for (int attempt = 0; attempt < PORT_RANGE; attempt++) {
			int candidate = requestedPort + attempt;
			try {
				HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", candidate), 0);
				created.createContext("/status", ControlServer::handleStatus);
				created.createContext("/command", ControlServer::handleCommand);
				created.createContext("/map", ControlServer::handleMap);
				created.createContext("/blocks", ControlServer::handleBlocks);
				executor = Executors.newFixedThreadPool(2);
				created.setExecutor(executor);
				created.start();
				server = created;
				port = candidate;
				JHenry.LOGGER.info("JHenry control server listening on 127.0.0.1:{}", port);
				return;
			} catch (IOException e) {
				// port in use; try the next one
			}
		}
		JHenry.LOGGER.error("JHenry: could not bind control server near port {}", requestedPort);
	}

	public static void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	public static boolean isRunning() {
		return server != null;
	}

	public static int port() {
		return port;
	}

	private static void handleStatus(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			respond(exchange, 401, "{\"error\":\"unauthorized\"}");
			return;
		}
		JsonObject status = onClientThread(ControlServer::buildStatus);
		respond(exchange, 200, status == null ? "{\"error\":\"unavailable\"}" : status.toString());
	}

	private static void handleCommand(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			respond(exchange, 401, "{\"error\":\"unauthorized\"}");
			return;
		}

		JsonObject response = new JsonObject();
		try {
			JsonObject request = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
			String action = request.has("action") ? request.get("action").getAsString() : "";
			JsonArray ores = request.has("ores") ? request.getAsJsonArray("ores") : null;
			JsonArray scaffolds = request.has("scaffolds") ? request.getAsJsonArray("scaffolds") : null;
			Boolean autoMine = request.has("autoMine") ? request.get("autoMine").getAsBoolean() : null;
			Boolean ok = onClientThread(() -> {
				if ("filter".equals(action)) {
					applyFilter(ores);
				} else if ("settings".equals(action)) {
					applySettings(scaffolds, autoMine);
				} else {
					execute(action);
				}
				return Boolean.TRUE;
			});
			response.addProperty("ok", ok != null);
			response.addProperty("action", action);
		} catch (Exception e) {
			response.addProperty("ok", false);
			response.addProperty("error", String.valueOf(e.getMessage()));
		}
		respond(exchange, 200, response.toString());
	}

	private static void handleMap(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			respond(exchange, 401, "{\"error\":\"unauthorized\"}");
			return;
		}
		byte[] png = onClientThread(ControlServer::renderMap);
		if (png == null) {
			respond(exchange, 503, "{\"error\":\"unavailable\"}");
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "image/png");
		exchange.sendResponseHeaders(200, png.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(png);
		}
	}

	private static void handleBlocks(HttpExchange exchange) throws IOException {
		if (!authorized(exchange)) {
			respond(exchange, 401, "{\"error\":\"unauthorized\"}");
			return;
		}
		String json = onClientThread(ControlServer::buildBlockList);
		if (json == null) {
			respond(exchange, 503, "{\"error\":\"unavailable\"}");
			return;
		}
		respond(exchange, 200, json);
	}

	private static String buildBlockList() {
		if (blockListJson != null) {
			return blockListJson;
		}
		JsonArray array = new JsonArray();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (block != Blocks.AIR) {
				array.add(BuiltInRegistries.BLOCK.getKey(block).toString());
			}
		}
		blockListJson = array.toString();
		return blockListJson;
	}

	private static byte[] renderMap() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return null;
		}

		final int radius = 10;
		final int scale = 4;
		final int size = (2 * radius + 1) * scale;

		java.awt.image.BufferedImage image =
				new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		BlockPos center = player.blockPosition();

		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				BlockPos pos = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
				int color = 0xFF101010;
				if (client.level.isLoaded(pos)) {
					BlockState state = client.level.getBlockState(pos);
					if (state.isAir()) {
						color = 0xFF1A1A1A;
					} else {
						MapColor mapColor = state.getMapColor(client.level, pos);
						color = 0xFF000000 | (mapColor.col & 0xFFFFFF);
					}
				}
				g.setColor(new java.awt.Color(color, true));
				g.fillRect((dx + radius) * scale, (dz + radius) * scale, scale, scale);
			}
		}

		for (TunnelPlan plan : MarkManager.plans()) {
			for (PlannedCell cell : plan.cells()) {
				BlockPos pos = cell.pos();
				if (pos.getY() != center.getY()) {
					continue;
				}
				int dx = pos.getX() - center.getX();
				int dz = pos.getZ() - center.getZ();
				if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
					continue;
				}
				if (client.level.isLoaded(pos) && client.level.getBlockState(pos).isAir()) {
					continue;
				}
				g.setColor(new java.awt.Color(segmentColor(cell.segment()), true));
				g.fillRect((dx + radius) * scale, (dz + radius) * scale, scale, scale);
			}
		}

		Direction facing = player.getDirection();
		g.setColor(new java.awt.Color(0xFFFF00));
		g.fillRect((radius + facing.getStepX()) * scale + scale / 2 - 1,
				(radius + facing.getStepZ()) * scale + scale / 2 - 1, 2, 2);
		g.setColor(java.awt.Color.WHITE);
		g.fillRect(radius * scale + scale / 2 - 1, radius * scale + scale / 2 - 1, 2, 2);
		g.dispose();

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			javax.imageio.ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	private static int segmentColor(Segment segment) {
		return switch (segment) {
			case OUTWARD -> 0xFF00FF00;
			case SIDESTEP -> 0xFFFFFF00;
			case RETURN -> 0xFF00AAFF;
			case RECONNECT -> 0xFFFF00FF;
		};
	}

	private static void execute(String action) {
		switch (action) {
			case "map" -> BotActions.map();
			case "dig" -> BotActions.dig();
			case "stop" -> BotActions.stopDig();
			case "resume" -> BotActions.resume();
			default -> {
			}
		}
	}

	private static void applyFilter(JsonArray ores) {
		if (ores == null) {
			return;
		}
		List<Block> blocks = new ArrayList<>();
		for (JsonElement element : ores) {
			Block block = OreFilter.resolve(element.getAsString());
			if (block != null) {
				blocks.add(block);
			}
		}
		OreFilter.setEnabled(blocks);
		Persistence.save();
	}

	private static void applySettings(JsonArray scaffolds, Boolean autoMine) {
		if (scaffolds != null) {
			List<Block> blocks = new ArrayList<>();
			for (JsonElement element : scaffolds) {
				Block block = ScaffoldFilter.resolve(element.getAsString());
				if (block != null) {
					blocks.add(block);
				}
			}
			ScaffoldFilter.setEnabled(blocks);
		}
		if (autoMine != null) {
			MiningSettings.setAutoMineOres(autoMine);
		}
		Persistence.save();
	}

	private static JsonObject buildStatus() {
		JsonObject status = new JsonObject();
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		boolean online = player != null && client.level != null;

		status.addProperty("online", online);
		status.addProperty("pid", ProcessHandle.current().pid());
		if (online) {
			status.addProperty("name", player.getName().getString());
			status.addProperty("uuid", player.getUUID().toString());
			status.addProperty("x", player.getX());
			status.addProperty("y", player.getY());
			status.addProperty("z", player.getZ());
			status.addProperty("dimension", client.level.dimension().identifier().toString());
			status.addProperty("health", player.getHealth());
			status.addProperty("food", player.getFoodData().getFoodLevel());

			JsonArray inventory = new JsonArray();
			Inventory inv = player.getInventory();
			for (int i = 0; i < 36; i++) {
				ItemStack stack = inv.getItem(i);
				if (!stack.isEmpty()) {
					JsonObject slot = new JsonObject();
					slot.addProperty("slot", i);
					slot.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
					slot.addProperty("count", stack.getCount());
					slot.addProperty("damage", stack.getDamageValue());
					slot.addProperty("maxDamage", stack.isDamageableItem() ? stack.getMaxDamage() : 0);
					inventory.add(slot);
				}
			}
			status.add("inventory", inventory);
		}
		status.addProperty("digging", TunnelDigger.isRunning());
		status.addProperty("mode", TunnelDigger.modeName());
		status.addProperty("lastAction", BotActions.lastAction());
		status.addProperty("actionSeq", BotActions.actionSeq());
		String error = TunnelDigger.lastError();
		status.addProperty("lastError", error == null ? "" : error);
		status.addProperty("blocksMined", TunnelDigger.blocksMined());
		status.addProperty("blocksTotal", TunnelDigger.blocksTotal());
		BlockPos target = TunnelDigger.currentTarget();
		status.addProperty("target", target == null ? "" : target.toShortString());
		if (online) {
			ItemStack tool = player.getMainHandItem();
			status.addProperty("toolDurability",
					tool.isDamageableItem() ? tool.getMaxDamage() - tool.getDamageValue() : -1);
			status.addProperty("freeSlots", player.getInventory().getFreeSlot());
		}
		status.addProperty("marks", MarkManager.marks().size());
		status.addProperty("plans", MarkManager.plans().size());
		status.addProperty("autoMine", MiningSettings.autoMineOres());
		JsonArray ores = new JsonArray();
		for (Block block : OreFilter.enabled()) {
			ores.add(OreFilter.idOf(block).toString());
		}
		status.add("ores", ores);
		JsonArray scaffolds = new JsonArray();
		for (Block block : ScaffoldFilter.enabled()) {
			scaffolds.add(ScaffoldFilter.idOf(block).toString());
		}
		status.add("scaffolds", scaffolds);
		status.addProperty("server", isRunning());
		return status;
	}

	private static boolean authorized(HttpExchange exchange) {
		String provided = exchange.getRequestHeaders().getFirst("X-JHenry-Token");
		return provided != null && provided.equals(token);
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static void respond(HttpExchange exchange, int code, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static <T> T onClientThread(Supplier<T> supplier) {
		Minecraft client = Minecraft.getInstance();
		CompletableFuture<T> future = new CompletableFuture<>();
		client.execute(() -> {
			try {
				future.complete(supplier.get());
			} catch (Throwable t) {
				future.completeExceptionally(t);
			}
		});
		try {
			return future.get(3, TimeUnit.SECONDS);
		} catch (Exception e) {
			return null;
		}
	}
}
