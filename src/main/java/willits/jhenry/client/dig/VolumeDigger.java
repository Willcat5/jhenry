package willits.jhenry.client.dig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willits.jhenry.client.look.Rotation;
import willits.jhenry.mapping.Partition;
import willits.jhenry.mapping.PartitionPattern;
import willits.jhenry.mapping.VolumeMapper;

public final class VolumeDigger {

	private static final float FACE_TOLERANCE = 5.0F;
	private static final float AIM_LERP = 0.35F;
	private static final float FLOOR_PITCH = 30.0F;
	private static final float UP_PITCH = -90.0F;
	private static final int MINE_CHECK_INTERVAL = 20;
	private static final int MINE_FAIL_LIMIT = 5;
	private static final int TOOL_DURABILITY_MIN = 10;

	private enum Mode {
		IDLE,
		RUNNING,
		PAUSED,
		DONE,
		FAILED
	}

	private record Run(Direction dir, int startGroup, int endGroup) {
	}

	private static Mode mode = Mode.IDLE;
	private static Partition partition;
	private static ResourceKey<Level> dimension;
	private static String volumeId;
	private static PartitionPattern pattern;
	private static List<BlockPos> cells;
	private static Set<BlockPos> partitionSet;
	private static List<Run> runs;
	private static int yCount;
	private static int groupCount;
	private static int walkY;
	private static int runIndex;
	private static int index;
	private static int mineTimer;
	private static int mineFails;
	private static boolean breakingThisInterval;
	private static BlockPos lastCheckedCell;
	private static String lastError;
	private static BlockPos finishedPartitionStart;
	private static ResourceKey<Level> finishedDimension;
	private static long finishedSeq;

	private VolumeDigger() {
	}

	public static void start(Partition part, ResourceKey<Level> dim, String id) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.level == null || player == null) {
			return;
		}

		BlockBreaker.stop();
		MoveController.stop();

		partition = part;
		dimension = dim;
		volumeId = id;
		pattern = part.pattern();
		walkY = part.start().getY();
		yCount = part.max().getY() - part.min().getY() + 1;
		cells = VolumeMapper.cells(part);
		partitionSet = new HashSet<>(cells);
		groupCount = yCount > 0 ? cells.size() / yCount : 0;
		runs = buildRuns();
		runIndex = 0;
		index = 0;
		mineTimer = 0;
		mineFails = 0;
		breakingThisInterval = false;
		lastCheckedCell = null;
		lastError = null;
		mode = Mode.RUNNING;
	}

	public static void stop() {
		BlockBreaker.stop();
		MoveController.stop();
		mode = Mode.IDLE;
		partition = null;
		dimension = null;
		volumeId = null;
		pattern = null;
		cells = null;
		partitionSet = null;
		runs = null;
		groupCount = 0;
		runIndex = 0;
		index = 0;
		mineTimer = 0;
		mineFails = 0;
		breakingThisInterval = false;
		lastCheckedCell = null;
	}

	public static void tick() {
		if (mode != Mode.RUNNING) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			stop();
			return;
		}

		if (pattern == PartitionPattern.FLOOR) {
			tickFloor(client, player);
		} else {
			tickUpper(client, player);
		}
	}

	private static void tickFloor(Minecraft client, LocalPlayer player) {
		if (runIndex >= runs.size()) {
			finish();
			return;
		}

		Run run = runs.get(runIndex);
		BlockPos end = stand(anchor(run.endGroup()));

		if (!isFacing(player, run.dir())) {
			setAim(player, run.dir().toYRot(), FLOOR_PITCH);
			return;
		}
		setAim(player, run.dir().toYRot(), FLOOR_PITCH);

		if (!MoveController.atTarget(player, end)) {
			attackIfInPartition(client, player);
			if (mode != Mode.RUNNING) {
				return;
			}
			if (!MoveController.isMoving()) {
				MoveController.moveTo(end);
			}
			return;
		}

		BlockPos straggler = firstNonAirInRun(client, run);
		if (straggler != null) {
			mineCell(client, player, straggler);
			return;
		}

		BlockBreaker.releaseAttack();
		mineTimer = 0;
		mineFails = 0;
		runIndex++;
	}

	private static void tickUpper(Minecraft client, LocalPlayer player) {
		if (index >= groupCount) {
			finish();
			return;
		}

		BlockPos current = anchor(index);
		BlockPos stand = stand(current);
		Direction dir = index == 0 ? partition.away() : directionFrom(anchor(index - 1), current);

		if (atColumn(player, stand)) {
			MoveController.stop();
			setAim(player, dir.toYRot(), UP_PITCH);

			BlockPos straggler = firstNonAirGroup(client, index);
			if (straggler == null) {
				BlockBreaker.releaseAttack();
				mineTimer = 0;
				mineFails = 0;
				index++;
				return;
			}
			attackIfInPartition(client, player);
			return;
		}

		if (!isFacing(player, dir)) {
			setAim(player, dir.toYRot(), UP_PITCH);
			return;
		}
		setAim(player, dir.toYRot(), UP_PITCH);
		BlockBreaker.releaseAttack();
		if (!MoveController.isMoving()) {
			MoveController.moveTo(stand);
		}
	}

	private static boolean atColumn(LocalPlayer player, BlockPos stand) {
		BlockPos pos = player.blockPosition();
		return pos.getX() == stand.getX() && pos.getZ() == stand.getZ();
	}

	private static List<Run> buildRuns() {
		List<Run> result = new ArrayList<>();
		if (groupCount == 0) {
			return result;
		}
		Direction[] dirs = new Direction[groupCount];
		for (int g = 0; g < groupCount; g++) {
			dirs[g] = g == 0 ? partition.away() : directionFrom(anchor(g - 1), anchor(g));
		}
		int g = 0;
		while (g < groupCount) {
			Direction dir = dirs[g];
			int start = g;
			while (g < groupCount && dirs[g] == dir) {
				g++;
			}
			result.add(new Run(dir, start, g - 1));
		}
		return result;
	}

	private static BlockPos anchor(int group) {
		return cells.get(group * yCount);
	}

	private static BlockPos stand(BlockPos a) {
		return new BlockPos(a.getX(), walkY, a.getZ());
	}

	private static BlockPos firstNonAirGroup(Minecraft client, int group) {
		for (int k = 0; k < yCount; k++) {
			BlockPos cell = cells.get(group * yCount + k);
			if (!client.level.getBlockState(cell).isAir()) {
				return cell;
			}
		}
		return null;
	}

	private static BlockPos firstNonAirInRun(Minecraft client, Run run) {
		for (int g = run.startGroup(); g <= run.endGroup(); g++) {
			BlockPos cell = firstNonAirGroup(client, g);
			if (cell != null) {
				return cell;
			}
		}
		return null;
	}

	private static void mineCell(Minecraft client, LocalPlayer player, BlockPos cell) {
		String hazard = hazardAt(client.level, cell);
		if (hazard != null) {
			pause("volume hazard at " + cell.toShortString() + " (" + hazard + ")");
			return;
		}
		ToolSelector.hold(player, client.level.getBlockState(cell));
		aimAt(player, cell);
		BlockBreaker.holdAttack();
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			breakingThisInterval = true;
		}
		tickStall(player);
	}

	private static void attackIfInPartition(Minecraft client, LocalPlayer player) {
		BlockPos target = hitTarget(client);
		if (target == null || !partitionSet.contains(target) || client.level.getBlockState(target).isAir()) {
			BlockBreaker.releaseAttack();
			return;
		}
		if (!target.equals(lastCheckedCell)) {
			lastCheckedCell = target.immutable();
			String hazard = hazardAt(client.level, target);
			if (hazard != null) {
				pause("volume hazard at " + target.toShortString() + " (" + hazard + ")");
				return;
			}
		}
		ToolSelector.hold(player, client.level.getBlockState(target));
		BlockBreaker.holdAttack();
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			breakingThisInterval = true;
		}
		tickStall(player);
	}

	private static void tickStall(LocalPlayer player) {
		mineTimer++;
		if (mineTimer < MINE_CHECK_INTERVAL) {
			return;
		}
		mineTimer = 0;
		String safety = checkSafety(player);
		if (safety != null) {
			fail(safety);
			return;
		}
		if (breakingThisInterval) {
			mineFails = 0;
		} else {
			mineFails++;
			if (mineFails >= MINE_FAIL_LIMIT) {
				fail("mining stalled");
			}
		}
		breakingThisInterval = false;
	}

	private static BlockPos hitTarget(Minecraft client) {
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			return ((BlockHitResult) client.hitResult).getBlockPos();
		}
		return null;
	}

	private static String hazardAt(Level level, BlockPos cell) {
		if (!level.isLoaded(cell)) {
			return "unloaded";
		}
		BlockState state = level.getBlockState(cell);
		if (state.isAir()) {
			return null;
		}
		if (!state.getFluidState().isEmpty()) {
			return "fluid";
		}
		if (state.getDestroySpeed(level, cell) < 0.0F) {
			return "unbreakable";
		}
		if (state.getBlock() instanceof FallingBlock) {
			return "gravel";
		}
		BlockPos above = cell.above();
		if (level.isLoaded(above) && level.getBlockState(above).getBlock() instanceof FallingBlock) {
			return "gravel above";
		}
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = cell.relative(dir);
			if (!level.isLoaded(neighbor)) {
				return "unloaded";
			}
			if (!level.getBlockState(neighbor).getFluidState().isEmpty()) {
				return "fluid adjacent " + dir;
			}
		}
		return null;
	}

	private static void setAim(LocalPlayer player, float yaw, float pitch) {
		float y = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * AIM_LERP;
		float p = player.getXRot() + (pitch - player.getXRot()) * AIM_LERP;
		Rotation.setAngles(player, y, p);
	}

	private static void aimAt(LocalPlayer player, BlockPos cell) {
		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(cell);
		double dx = center.x - eye.x;
		double dy = center.y - eye.y;
		double dz = center.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0F, 90.0F);
		setAim(player, yaw, pitch);
	}

	private static boolean isFacing(LocalPlayer player, Direction dir) {
		return Math.abs(Mth.wrapDegrees(player.getYRot() - dir.toYRot())) < FACE_TOLERANCE;
	}

	private static Direction directionFrom(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (dx == 1 && dz == 0) {
			return Direction.EAST;
		}
		if (dx == -1 && dz == 0) {
			return Direction.WEST;
		}
		if (dz == 1 && dx == 0) {
			return Direction.SOUTH;
		}
		if (dz == -1 && dx == 0) {
			return Direction.NORTH;
		}
		return Direction.NORTH;
	}

	private static String checkSafety(LocalPlayer player) {
		ItemStack tool = player.getMainHandItem();
		if (tool.isDamageableItem()) {
			int remaining = tool.getMaxDamage() - tool.getDamageValue();
			if (remaining <= TOOL_DURABILITY_MIN) {
				return "tool durability low (" + remaining + ")";
			}
		}
		if (player.getInventory().getFreeSlot() == -1) {
			return "inventory full";
		}
		return null;
	}

	private static void finish() {
		MoveController.stop();
		BlockBreaker.stop();
		if (partition != null) {
			finishedPartitionStart = partition.start().immutable();
			finishedDimension = dimension;
			finishedSeq++;
		}
		mode = Mode.DONE;
		message("JHenry: partition complete - move bot to the next partition");
	}

	private static void fail(String reason) {
		BlockBreaker.stop();
		MoveController.stop();
		mode = Mode.FAILED;
		lastError = reason;
		message("JHenry: volume dig failed - " + reason);
	}

	private static void pause(String reason) {
		BlockBreaker.stop();
		MoveController.stop();
		mode = Mode.PAUSED;
		lastError = reason;
		message("JHenry: volume paused - " + reason);
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(Component.literal(text), false);
		}
	}

	public static boolean isRunning() {
		return mode == Mode.RUNNING;
	}

	public static boolean isActive() {
		return mode != Mode.IDLE;
	}

	public static boolean isPaused() {
		return mode == Mode.PAUSED;
	}

	public static void pauseForUser() {
		if (mode == Mode.RUNNING) {
			pause("paused");
		}
	}

	public static void resume() {
		if (mode != Mode.PAUSED && mode != Mode.FAILED) {
			message("JHenry: nothing to resume");
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || cells == null) {
			return;
		}

		BlockPos pos = player.blockPosition();
		int nearest = 0;
		double best = Double.MAX_VALUE;
		for (int g = 0; g < groupCount; g++) {
			double distance = anchor(g).distSqr(pos);
			if (distance < best) {
				best = distance;
				nearest = g;
			}
		}

		if (pattern == PartitionPattern.FLOOR) {
			runIndex = 0;
			for (int i = 0; i < runs.size(); i++) {
				Run run = runs.get(i);
				if (nearest >= run.startGroup() && nearest <= run.endGroup()) {
					runIndex = i;
					break;
				}
			}
		} else {
			index = nearest;
		}

		lastCheckedCell = null;
		lastError = null;
		mode = Mode.RUNNING;
		message("JHenry: resuming volume");
	}

	public static String volumeId() {
		return volumeId;
	}

	public static int partitionIndex() {
		return partition == null ? -1 : partition.index();
	}

	public static String modeName() {
		return mode.name();
	}

	public static String lastError() {
		return lastError;
	}

	public static int blocksMined() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || cells == null) {
			return 0;
		}
		int mined = 0;
		for (BlockPos cell : cells) {
			if (client.level.getBlockState(cell).isAir()) {
				mined++;
			}
		}
		return mined;
	}

	public static int blocksTotal() {
		return cells == null ? 0 : cells.size();
	}

	public static BlockPos currentTarget() {
		if (cells == null) {
			return null;
		}
		if (pattern == PartitionPattern.FLOOR) {
			return runIndex < runs.size() ? anchor(runs.get(runIndex).endGroup()) : null;
		}
		return index < groupCount ? anchor(index) : null;
	}

	public static BlockPos finishedPartitionStart() {
		return finishedPartitionStart;
	}

	public static ResourceKey<Level> finishedDimension() {
		return finishedDimension;
	}

	public static long finishedSeq() {
		return finishedSeq;
	}
}
