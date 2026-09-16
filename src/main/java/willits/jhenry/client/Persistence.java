package willits.jhenry.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import willits.jhenry.JHenry;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.OreFilter;
import willits.jhenry.mapping.PlannedCell;
import willits.jhenry.mapping.ScaffoldFilter;
import willits.jhenry.mapping.Segment;
import willits.jhenry.mapping.StopReason;
import willits.jhenry.mapping.TunnelMark;
import willits.jhenry.mapping.TunnelPlan;

public final class Persistence {

	private static final Gson GSON = new Gson();
	private static UUID loadedUuid;

	private Persistence() {
	}

	public static void load(UUID uuid) {
		loadedUuid = uuid;
		MarkManager.clearMarks();
		MarkManager.clearPlans();

		Path file = fileFor(uuid);
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null) {
				return;
			}
			if (root.has("marks")) {
				for (JsonElement element : root.getAsJsonArray("marks")) {
					TunnelMark mark = markFromJson(element.getAsJsonObject());
					if (mark != null) {
						MarkManager.addMark(mark);
					}
				}
			}
			if (root.has("plans")) {
				for (JsonElement element : root.getAsJsonArray("plans")) {
					TunnelPlan plan = planFromJson(element.getAsJsonObject());
					if (plan != null) {
						MarkManager.addPlan(plan);
					}
				}
			}
			if (root.has("oreFilter")) {
				java.util.List<Block> blocks = new java.util.ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("oreFilter")) {
					Block block = OreFilter.resolve(element.getAsString());
					if (block != null) {
						blocks.add(block);
					}
				}
				OreFilter.setEnabled(blocks);
			}
			if (root.has("scaffolds")) {
				java.util.List<Block> blocks = new java.util.ArrayList<>();
				for (JsonElement element : root.getAsJsonArray("scaffolds")) {
					Block block = ScaffoldFilter.resolve(element.getAsString());
					if (block != null) {
						blocks.add(block);
					}
				}
				ScaffoldFilter.setEnabled(blocks);
			}
			if (root.has("autoMine")) {
				MiningSettings.setAutoMineOres(root.get("autoMine").getAsBoolean());
			}
			if (root.has("maxBlocks")) {
				MarkManager.config().maxBlocks = root.get("maxBlocks").getAsInt();
			}
			if (root.has("maxBlocksOverride")) {
				MarkManager.config().maxBlocksOverride = root.get("maxBlocksOverride").getAsBoolean();
			}
			if (root.has("autoTool")) {
				MiningSettings.setAutoTool(root.get("autoTool").getAsBoolean());
			}
			if (root.has("peek")) {
				MiningSettings.setPeek(root.get("peek").getAsBoolean());
			}
			if (root.has("pauseOnDamage")) {
				MiningSettings.setPauseOnDamage(root.get("pauseOnDamage").getAsBoolean());
			}
		} catch (Exception e) {
			JHenry.LOGGER.error("JHenry: failed to load data for {}", uuid, e);
		}
	}

	public static void save() {
		if (loadedUuid == null) {
			return;
		}
		Path file = fileFor(loadedUuid);
		try {
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();

			JsonArray marks = new JsonArray();
			for (TunnelMark mark : MarkManager.marks()) {
				marks.add(markToJson(mark));
			}
			root.add("marks", marks);

			JsonArray plans = new JsonArray();
			for (TunnelPlan plan : MarkManager.plans()) {
				plans.add(planToJson(plan));
			}
			root.add("plans", plans);

			JsonArray ores = new JsonArray();
			for (Block block : OreFilter.enabled()) {
				ores.add(OreFilter.idOf(block).toString());
			}
			root.add("oreFilter", ores);

			JsonArray scaffolds = new JsonArray();
			for (Block block : ScaffoldFilter.enabled()) {
				scaffolds.add(ScaffoldFilter.idOf(block).toString());
			}
			root.add("scaffolds", scaffolds);
			root.addProperty("autoMine", MiningSettings.autoMineOres());
			root.addProperty("maxBlocks", MarkManager.config().maxBlocks);
			root.addProperty("maxBlocksOverride", MarkManager.config().maxBlocksOverride);
			root.addProperty("autoTool", MiningSettings.autoTool());
			root.addProperty("peek", MiningSettings.peek());
			root.addProperty("pauseOnDamage", MiningSettings.pauseOnDamage());

			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(root, writer);
			}
		} catch (Exception e) {
			JHenry.LOGGER.error("JHenry: failed to save data for {}", loadedUuid, e);
		}
	}

	public static UUID loadedUuid() {
		return loadedUuid;
	}

	private static Path fileFor(UUID uuid) {
		return FabricLoader.getInstance().getConfigDir().resolve("jhenry").resolve(uuid + ".json");
	}

	private static JsonObject posToJson(BlockPos pos) {
		JsonObject object = new JsonObject();
		object.addProperty("x", pos.getX());
		object.addProperty("y", pos.getY());
		object.addProperty("z", pos.getZ());
		return object;
	}

	private static BlockPos posFromJson(JsonObject object) {
		return new BlockPos(object.get("x").getAsInt(), object.get("y").getAsInt(), object.get("z").getAsInt());
	}

	private static JsonObject markToJson(TunnelMark mark) {
		JsonObject object = new JsonObject();
		object.add("pos", posToJson(mark.entrance()));
		object.addProperty("facing", mark.facing().name());
		object.addProperty("open", mark.open());
		object.addProperty("dimension", mark.dimension().identifier().toString());
		return object;
	}

	private static TunnelMark markFromJson(JsonObject object) {
		try {
			BlockPos pos = posFromJson(object.getAsJsonObject("pos"));
			Direction facing = Direction.valueOf(object.get("facing").getAsString());
			boolean open = object.get("open").getAsBoolean();
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
					Identifier.parse(object.get("dimension").getAsString()));
			return new TunnelMark(pos, facing, open, dimension);
		} catch (Exception e) {
			return null;
		}
	}

	private static JsonObject planToJson(TunnelPlan plan) {
		JsonObject object = new JsonObject();
		object.add("mark", markToJson(plan.mark()));
		object.addProperty("direction", plan.direction().name());
		object.addProperty("length", plan.length());
		object.addProperty("stopReason", plan.stopReason().name());
		object.addProperty("blockCount", plan.blockCount());
		object.addProperty("detail", plan.detail());
		object.add("stopPos", plan.stopPos() == null ? JsonNull.INSTANCE : posToJson(plan.stopPos()));
		object.addProperty("stopSegment", plan.stopSegment().name());
		object.add("returnStopPos",
				plan.returnStopPos() == null ? JsonNull.INSTANCE : posToJson(plan.returnStopPos()));

		JsonArray cells = new JsonArray();
		for (PlannedCell cell : plan.cells()) {
			JsonObject entry = new JsonObject();
			entry.add("pos", posToJson(cell.pos()));
			entry.addProperty("segment", cell.segment().name());
			cells.add(entry);
		}
		object.add("cells", cells);
		return object;
	}

	private static TunnelPlan planFromJson(JsonObject object) {
		try {
			TunnelMark mark = markFromJson(object.getAsJsonObject("mark"));
			if (mark == null) {
				return null;
			}
			Direction direction = Direction.valueOf(object.get("direction").getAsString());
			int length = object.get("length").getAsInt();
			StopReason stopReason = StopReason.valueOf(object.get("stopReason").getAsString());
			int blockCount = object.get("blockCount").getAsInt();
			String detail = object.has("detail") ? object.get("detail").getAsString() : "";
			BlockPos stopPos = object.has("stopPos") && !object.get("stopPos").isJsonNull()
					? posFromJson(object.getAsJsonObject("stopPos")) : null;
			Segment stopSegment = object.has("stopSegment")
					? Segment.valueOf(object.get("stopSegment").getAsString()) : Segment.OUTWARD;
			BlockPos returnStopPos = object.has("returnStopPos") && !object.get("returnStopPos").isJsonNull()
					? posFromJson(object.getAsJsonObject("returnStopPos")) : null;

			java.util.List<PlannedCell> cells = new java.util.ArrayList<>();
			if (object.has("cells")) {
				for (JsonElement element : object.getAsJsonArray("cells")) {
					JsonObject entry = element.getAsJsonObject();
					BlockPos pos = posFromJson(entry.getAsJsonObject("pos"));
					Segment segment = Segment.valueOf(entry.get("segment").getAsString());
					cells.add(new PlannedCell(pos, segment));
				}
			}

			return new TunnelPlan(mark, direction, length, cells, stopReason, blockCount, detail,
					stopPos, stopSegment, returnStopPos);
		} catch (Exception e) {
			return null;
		}
	}
}
