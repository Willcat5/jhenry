package willits.jhenry.client.dig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willits.jhenry.client.look.LookController;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.OreFilter;
import willits.jhenry.mapping.PlannedCell;
import willits.jhenry.mapping.ScaffoldFilter;
import willits.jhenry.mapping.Segment;
import willits.jhenry.mapping.TunnelPlan;

public final class TunnelDigger {

	private static final float FACE_TOLERANCE = 5.0F;
	private static final float AIM_LERP = 0.35F;
	private static final int MINE_CHECK_INTERVAL = 20;
	private static final int MINE_FAIL_LIMIT = 5;
	private static final int TOOL_DURABILITY_MIN = 10;
	private static final int FLOOR_SCAN_DEPTH = 32;
	private static final int VEIN_CAP = 512;
	private static final int ORE_CYCLE_SLICES = 3;
	private static final int ORE_STALL_LIMIT = 200;
	private static final int[][] RING_ORDER = {
			{-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}
	};

	private enum Mode {
		IDLE,
		RUNNING,
		PAUSED,
		DONE,
		FAILED
	}

	private enum OrePhase {
		NONE,
		REPOSITION,
		MINE,
		PLACE
	}

	private record Run(Direction dir, List<BlockPos> cells, boolean dig) {
	}

	private static Mode mode = Mode.IDLE;
	private static List<Run> runs;
	private static int runIndex;
	private static TunnelPlan lastPlan;
	private static int mineTimer;
	private static int mineFails;
	private static boolean breakingThisInterval;
	private static boolean aimLocked;
	private static BlockPos aimTarget;
	private static BlockPos lastCheckedTarget;
	private static String lastError;
	private static final Set<BlockPos> checkedExposure = new HashSet<>();
	private static List<BlockPos> planCells;
	private static BlockPos currentTarget;

	private static OrePhase orePhase = OrePhase.NONE;
	private static Direction oreDir;
	private static BlockPos oreOrigin;
	private static List<BlockPos> oreVein;
	private static List<BlockPos> oreMine;
	private static List<BlockPos> orePlace;
	private static int oreMineIndex;
	private static int orePlaceIndex;
	private static int oreMineStall;
	private static BlockPos oreMineLastTarget;
	private static final Set<BlockPos> oreOccluders = new HashSet<>();
	private static final Set<BlockPos> oreMined = new HashSet<>();
	private static int oreSavedSlot = -1;

	private TunnelDigger() {
	}

	public static void start(TunnelPlan plan) {
		start(plan, false);
	}

	public static void start(TunnelPlan plan, boolean force) {
		if (plan.cells().isEmpty()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (!force && !player.blockPosition().equals(plan.mark().entrance())) {
			message("JHenry: stand at the entrance to start digging");
			return;
		}

		BlockBreaker.stop();
		MoveController.stop();
		LookController.stop();

		lastPlan = plan;
		List<BlockPos> digCells = new ArrayList<>();
		for (PlannedCell cell : plan.cells()) {
			digCells.add(cell.pos());
		}

		runs = new ArrayList<>();
		runs.addAll(buildRuns(digCells, plan.mark().entrance(), true));

		List<BlockPos> exitCells = buildExitRoute(plan);
		BlockPos exitStart = digCells.get(digCells.size() - 1);
		runs.addAll(buildRuns(exitCells, exitStart, false));

		runIndex = 0;
		mineTimer = 0;
		mineFails = 0;
		breakingThisInterval = false;
		aimLocked = false;
		aimTarget = null;
		lastCheckedTarget = null;
		lastError = null;
		checkedExposure.clear();
		MarkManager.clearExposedOres();
		planCells = new ArrayList<>(digCells);
		currentTarget = null;
		resetOreTask();
		mode = Mode.RUNNING;
	}

	public static void stop() {
		BlockBreaker.stop();
		MoveController.stop();
		LookController.stop();
		resetOreTask();
		mode = Mode.IDLE;
		runs = null;
		runIndex = 0;
		mineTimer = 0;
		mineFails = 0;
		breakingThisInterval = false;
		aimLocked = false;
		aimTarget = null;
		lastCheckedTarget = null;
		currentTarget = null;
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

		if (orePhase != OrePhase.NONE) {
			tickOreTask(client, player);
			return;
		}

		if (runIndex >= runs.size()) {
			finish();
			return;
		}

		Run run = runs.get(runIndex);
		BlockPos end = run.cells().get(run.cells().size() - 1);

		if (run.dig()) {
			tickDigRun(client, player, run, end);
		} else {
			tickWalkRun(player, run, end);
		}
	}

	private static void tickDigRun(Minecraft client, LocalPlayer player, Run run, BlockPos end) {
		if (isOffPath(player)) {
			fail("fell out of path");
			return;
		}

		if (isStanding(player)
				&& (lastPlan == null || !player.blockPosition().equals(lastPlan.mark().entrance()))) {
			fail("stood up while digging");
			return;
		}

		BlockPos botCell = player.blockPosition();
		if (checkedExposure.add(botCell)) {
			checkOreExposure(client, botCell, run.dir());
			if (orePhase != OrePhase.NONE || mode != Mode.RUNNING) {
				return;
			}
		}

		BlockPos nearest = null;
		BlockPos furthest = null;
		for (BlockPos cell : run.cells()) {
			if (!client.level.getBlockState(cell).isAir()) {
				if (nearest == null) {
					nearest = cell;
				}
				furthest = cell;
			}
		}

		if (furthest != null) {
			currentTarget = nearest;

			if (!nearest.equals(lastCheckedTarget)) {
				lastCheckedTarget = nearest.immutable();
				String danger = dangerAt(client.level, nearest, run.dir().getOpposite());
				if (danger != null) {
					fail("hazard ahead at " + nearest.toShortString() + " (" + danger + ")");
					return;
				}
			}

			if (!aimLocked && player.getEyeY() - player.getY() < 1.0D) {
				aimLocked = true;
				aimTarget = furthest;
			}

			BlockPos target = aimLocked && aimTarget != null ? aimTarget : nearest;
			aimAt(player, target);
			BlockBreaker.holdAttack();

			if (client.gameMode != null && client.gameMode.isDestroying()) {
				breakingThisInterval = true;
			}

			mineTimer++;
			if (mineTimer >= MINE_CHECK_INTERVAL) {
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
					BlockBreaker.holdAttack();
					if (mineFails >= MINE_FAIL_LIMIT) {
						fail("mining stalled");
						return;
					}
				}
				breakingThisInterval = false;
			}
		} else {
			BlockBreaker.releaseAttack();
			mineTimer = 0;
			mineFails = 0;
			breakingThisInterval = false;
		}

		if (!MoveController.atTarget(player, end)) {
			if (!MoveController.isMoving()) {
				MoveController.moveTo(end);
			}
			return;
		}

		BlockBreaker.releaseAttack();
		MoveController.stop();
		mineTimer = 0;
		mineFails = 0;
		breakingThisInterval = false;
		aimLocked = false;
		aimTarget = null;
		lastCheckedTarget = null;
		currentTarget = null;
		runIndex++;
	}

	private static void tickWalkRun(LocalPlayer player, Run run, BlockPos end) {
		if (LookController.isActive() || !isFacing(player, run.dir())) {
			LookController.face(run.dir());
			return;
		}

		if (!MoveController.atTarget(player, end)) {
			if (!MoveController.isMoving()) {
				MoveController.moveTo(end);
			}
			return;
		}

		MoveController.stop();
		runIndex++;
	}

	private static void aimAt(LocalPlayer player, BlockPos cell) {
		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(cell);
		double dx = center.x - eye.x;
		double dy = center.y - eye.y;
		double dz = center.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float targetPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0F, 90.0F);

		float yaw = player.getYRot() + Mth.wrapDegrees(targetYaw - player.getYRot()) * AIM_LERP;
		float pitch = player.getXRot() + (targetPitch - player.getXRot()) * AIM_LERP;

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
	}

	private static List<Run> buildRuns(List<BlockPos> cells, BlockPos start, boolean dig) {
		List<Run> result = new ArrayList<>();
		BlockPos prev = start;
		Direction currentDir = null;
		List<BlockPos> currentCells = null;

		for (BlockPos cell : cells) {
			Direction dir = directionFrom(prev, cell);
			if (dir == null) {
				break;
			}
			if (currentDir == null || currentDir != dir) {
				currentDir = dir;
				currentCells = new ArrayList<>();
				result.add(new Run(dir, currentCells, dig));
			}
			currentCells.add(cell);
			prev = cell;
		}
		return result;
	}

	private static List<BlockPos> buildExitRoute(TunnelPlan plan) {
		List<PlannedCell> cells = plan.cells();
		BlockPos last = cells.get(cells.size() - 1).pos();
		Direction dir = plan.direction();
		BlockPos entrance = plan.mark().entrance();

		int endpointIndex = -1;
		for (int i = 0; i < cells.size() - 1; i++) {
			Segment segment = cells.get(i).segment();
			if ((segment == Segment.OUTWARD || segment == Segment.SIDESTEP)
					&& isAdjacent(last, cells.get(i).pos())) {
				endpointIndex = i;
				break;
			}
		}

		List<BlockPos> route = new ArrayList<>();
		if (endpointIndex >= 0) {
			for (int i = endpointIndex; i >= 0; i--) {
				route.add(cells.get(i).pos());
			}
			route.add(entrance);
			route.add(entrance.relative(dir.getOpposite()));
		} else {
			route.add(last.relative(dir.getOpposite()));
		}
		return route;
	}

	private static void finish() {
		MoveController.stop();
		BlockBreaker.stop();
		if (lastPlan != null) {
			MarkManager.plans().remove(lastPlan);
		}
		mode = Mode.DONE;
		message("JHenry: tunnel complete");
	}

	private static void fail(String reason) {
		stop();
		mode = Mode.FAILED;
		lastError = reason;
		message("JHenry: dig failed - " + reason);
	}

	private static void pause(String reason) {
		BlockBreaker.stop();
		MoveController.stop();
		BlockPlacer.stop();
		mode = Mode.PAUSED;
		lastError = reason;
		message("JHenry: paused - " + reason + " (mine it, then resume)");
	}

	private static void checkOreExposure(Minecraft client, BlockPos cell, Direction dir) {
		Set<BlockPos> visited = new HashSet<>();
		List<BlockPos> vein = new ArrayList<>();
		for (BlockPos edge : perpendicularEdges(cell, dir)) {
			if (!client.level.isLoaded(edge)) {
				continue;
			}
			if (OreFilter.isOre(client.level.getBlockState(edge))) {
				floodFillVein(client, edge, visited, vein);
			}
		}
		if (vein.isEmpty()) {
			return;
		}

		for (BlockPos ore : vein) {
			MarkManager.addExposedOre(ore);
		}

		if (!MiningSettings.autoMineOres()) {
			pause("ore exposed");
			return;
		}
		if (ScaffoldFilter.isEmpty()) {
			pause("ore exposed (no placement blocks selected)");
			return;
		}

		List<BlockPos> mineable = new ArrayList<>();
		for (BlockPos ore : vein) {
			int[] perp = perpOffset(ore, cell, dir);
			if (Math.abs(perp[0]) > 1 || Math.abs(perp[1]) > 1) {
				continue;
			}
			int slice = sliceOf(ore, cell, dir);
			if (slice < 0 || slice >= ORE_CYCLE_SLICES) {
				continue;
			}
			mineable.add(ore);
		}
		if (mineable.isEmpty()) {
			return;
		}

		startOreTask(cell, dir, mineable);
	}

	private static void floodFillVein(Minecraft client, BlockPos start, Set<BlockPos> visited, List<BlockPos> vein) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos origin = start.immutable();
		queue.add(origin);
		visited.add(origin);

		while (!queue.isEmpty() && vein.size() < VEIN_CAP) {
			BlockPos ore = queue.poll();
			vein.add(ore);

			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos neighbor = ore.offset(dx, dy, dz);
						if (!visited.add(neighbor)) {
							continue;
						}
						if (!client.level.isLoaded(neighbor)) {
							continue;
						}
						if (OreFilter.isOre(client.level.getBlockState(neighbor))) {
							queue.add(neighbor.immutable());
						}
					}
				}
			}
		}
	}

	private static List<BlockPos> perpendicularEdges(BlockPos center, Direction dir) {
		List<BlockPos> cells = new ArrayList<>();
		Direction.Axis axis = dir.getAxis();
		if (axis != Direction.Axis.X) {
			cells.add(center.offset(1, 0, 0));
			cells.add(center.offset(-1, 0, 0));
		}
		if (axis != Direction.Axis.Y) {
			cells.add(center.offset(0, 1, 0));
			cells.add(center.offset(0, -1, 0));
		}
		if (axis != Direction.Axis.Z) {
			cells.add(center.offset(0, 0, 1));
			cells.add(center.offset(0, 0, -1));
		}
		return cells;
	}

	private static int[] perpOffset(BlockPos cell, BlockPos origin, Direction dir) {
		int dx = cell.getX() - origin.getX();
		int dy = cell.getY() - origin.getY();
		int dz = cell.getZ() - origin.getZ();
		return switch (dir.getAxis()) {
			case X -> new int[] {dy, dz};
			case Z -> new int[] {dx, dy};
			default -> new int[] {dx, dz};
		};
	}

	private static int sliceOf(BlockPos cell, BlockPos origin, Direction dir) {
		return switch (dir.getAxis()) {
			case X -> (cell.getX() - origin.getX()) * dir.getStepX();
			case Z -> (cell.getZ() - origin.getZ()) * dir.getStepZ();
			default -> (cell.getY() - origin.getY()) * dir.getStepY();
		};
	}

	private static boolean isAxisCell(BlockPos cell, BlockPos origin, Direction dir) {
		int[] perp = perpOffset(cell, origin, dir);
		return perp[0] == 0 && perp[1] == 0;
	}

	private static void startOreTask(BlockPos origin, Direction dir, List<BlockPos> mineable) {
		oreDir = dir;
		oreOrigin = origin.immutable();
		oreVein = new ArrayList<>(mineable);
		oreOccluders.clear();
		oreMined.clear();
		oreMine = orderMine(origin, dir, new HashSet<>(mineable), Minecraft.getInstance());
		orePlace = new ArrayList<>();
		oreMineIndex = 0;
		orePlaceIndex = 0;
		oreMineStall = 0;
		oreMineLastTarget = null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			oreSavedSlot = player.getInventory().getSelectedSlot();
		}
		selectToolSlot();
		MoveController.stop();
		orePhase = OrePhase.REPOSITION;
		MoveController.nudgeBackward();
	}

	private static List<BlockPos> orderMine(BlockPos origin, Direction dir, Set<BlockPos> vein, Minecraft client) {
		List<BlockPos> ordered = new ArrayList<>();
		for (int slice = 0; slice < ORE_CYCLE_SLICES; slice++) {
			BlockPos center = origin.relative(dir, slice);
			if (!client.level.getBlockState(center).isAir()) {
				ordered.add(center.immutable());
			}
		}

		int start = -1;
		for (int i = 0; i < RING_ORDER.length; i++) {
			if (isCornerOffset(RING_ORDER[i])) {
				continue;
			}
			if (ringHasOre(origin, dir, vein, RING_ORDER[i])) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			for (int i = 0; i < RING_ORDER.length; i++) {
				if (ringHasOre(origin, dir, vein, RING_ORDER[i])) {
					start = i;
					break;
				}
			}
		}
		if (start < 0) {
			return ordered;
		}

		for (int k = 0; k < RING_ORDER.length; k++) {
			int[] position = RING_ORDER[(start + k) % RING_ORDER.length];
			for (int slice = 0; slice < ORE_CYCLE_SLICES; slice++) {
				BlockPos cell = ringCell(origin, dir, slice, position[0], position[1]);
				if (vein.contains(cell)) {
					ordered.add(cell);
				}
			}
		}
		return ordered;
	}

	private static boolean ringHasOre(BlockPos origin, Direction dir, Set<BlockPos> vein, int[] position) {
		for (int slice = 0; slice < ORE_CYCLE_SLICES; slice++) {
			if (vein.contains(ringCell(origin, dir, slice, position[0], position[1]))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCornerOffset(int[] position) {
		return position[0] != 0 && position[1] != 0;
	}

	private static BlockPos ringCell(BlockPos origin, Direction dir, int slice, int a, int b) {
		BlockPos center = origin.relative(dir, slice);
		return switch (dir.getAxis()) {
			case X -> center.offset(0, a, b);
			case Z -> center.offset(a, b, 0);
			default -> center.offset(a, 0, b);
		};
	}

	private static int perpOrder(BlockPos cell) {
		int[] perp = perpOffset(cell, oreOrigin, oreDir);
		return (perp[0] + 1) * 3 + (perp[1] + 1);
	}

	private static boolean isCorner(BlockPos cell) {
		int[] perp = perpOffset(cell, oreOrigin, oreDir);
		return perp[0] != 0 && perp[1] != 0;
	}

	private static void tickOreTask(Minecraft client, LocalPlayer player) {
		switch (orePhase) {
			case REPOSITION -> {
				if (!MoveController.isNudging()) {
					orePhase = OrePhase.MINE;
				}
			}
			case MINE -> tickOreMine(client, player);
			case PLACE -> tickOrePlace(client, player);
			default -> {
			}
		}
	}

	private static void tickOreMine(Minecraft client, LocalPlayer player) {
		while (oreMineIndex < oreMine.size()) {
			BlockPos candidate = oreMine.get(oreMineIndex);
			if (!client.level.getBlockState(candidate).isAir()) {
				break;
			}
			oreMined.add(candidate);
			oreMineIndex++;
		}
		if (oreMineIndex >= oreMine.size()) {
			BlockBreaker.stop();
			buildOrePlace(client);
			orePhase = OrePhase.PLACE;
			return;
		}

		String safety = checkSafety(player);
		if (safety != null) {
			fail(safety);
			return;
		}

		BlockPos cell = oreMine.get(oreMineIndex);

		BlockPos effective = cell;
		if (!LookController.isActive() && client.hitResult != null
				&& client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockPos hitPos = ((BlockHitResult) client.hitResult).getBlockPos();
			if (!hitPos.equals(cell) && inScope(hitPos)) {
				effective = hitPos.immutable();
			}
		}

		if (effective.equals(oreMineLastTarget)) {
			if (++oreMineStall > ORE_STALL_LIMIT) {
				fail("blocked at " + effective.toShortString());
				return;
			}
		} else {
			String invalid = invalidToMine(client, effective);
			if (invalid != null) {
				fail("cannot mine " + effective.toShortString() + " (" + invalid + ")");
				return;
			}
			if (!effective.equals(cell)) {
				oreOccluders.add(effective);
			}
			oreMined.add(effective);
			oreMineLastTarget = effective;
			oreMineStall = 0;
		}

		if (BlockBreaker.target() == null || !BlockBreaker.target().equals(effective)) {
			BlockBreaker.breakBlock(effective);
		}
	}

	private static void buildOrePlace(Minecraft client) {
		orePlace = new ArrayList<>();
		for (BlockPos cell : oreMine) {
			if (isAxisCell(cell, oreOrigin, oreDir)) {
				continue;
			}
			if (client.level.getBlockState(cell).isAir()) {
				orePlace.add(cell);
			}
		}
		orePlace.addAll(oreOccluders);
		orePlace.sort(Comparator
				.comparingInt((BlockPos cell) -> isCorner(cell) ? 0 : 1)
				.thenComparingInt(TunnelDigger::perpOrder)
				.thenComparingInt(cell -> -sliceOf(cell, oreOrigin, oreDir)));
		orePlaceIndex = 0;
	}

	private static boolean inScope(BlockPos pos) {
		int[] perp = perpOffset(pos, oreOrigin, oreDir);
		if (Math.abs(perp[0]) <= 1 && Math.abs(perp[1]) <= 1) {
			return true;
		}
		return isAxisCell(pos, oreOrigin, oreDir);
	}

	private static String invalidToMine(Minecraft client, BlockPos cell) {
		if (!client.level.isLoaded(cell)) {
			return "unloaded";
		}
		BlockState state = client.level.getBlockState(cell);
		if (state.isAir()) {
			return "air";
		}
		if (!state.getFluidState().isEmpty()) {
			return "fluid";
		}
		if (state.getDestroySpeed(client.level, cell) < 0.0F) {
			return "unbreakable";
		}
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = cell.relative(direction);
			if (oreMined.contains(neighbor) || isAxisCell(neighbor, oreOrigin, oreDir)) {
				continue;
			}
			if (!client.level.isLoaded(neighbor)) {
				return "unloaded";
			}
			if (!client.level.getBlockState(neighbor).isSolid()) {
				return "exposes " + direction;
			}
		}
		BlockPos above = cell.above();
		if (client.level.isLoaded(above)
				&& client.level.getBlockState(above).getBlock() instanceof FallingBlock) {
			return "falling block above";
		}
		return null;
	}

	private static void tickOrePlace(Minecraft client, LocalPlayer player) {
		if (orePlaceIndex >= orePlace.size()) {
			BlockPlacer.stop();
			endOreTask();
			return;
		}

		BlockPos cell = orePlace.get(orePlaceIndex);
		if (!client.level.getBlockState(cell).isAir()) {
			orePlaceIndex++;
			return;
		}

		if (!selectPlacementSlot(player)) {
			fail("no placement block in hotbar");
			return;
		}

		if (!BlockPlacer.isPlacing() || !cell.equals(BlockPlacer.target())) {
			BlockPlacer.place(cell);
		}

		BlockPlacer.Status status = BlockPlacer.tick();
		if (status == BlockPlacer.Status.DONE) {
			orePlaceIndex++;
		} else if (status == BlockPlacer.Status.FAILED) {
			fail("could not place block at " + cell.toShortString());
		}
	}

	private static void endOreTask() {
		BlockBreaker.stop();
		MoveController.stop();
		BlockPlacer.stop();
		restoreSlot();
		orePhase = OrePhase.NONE;
		oreVein = null;
		oreMine = null;
		orePlace = null;
		oreOccluders.clear();
		oreMined.clear();
	}

	private static void resetOreTask() {
		BlockPlacer.stop();
		restoreSlot();
		orePhase = OrePhase.NONE;
		oreDir = null;
		oreOrigin = null;
		oreVein = null;
		oreMine = null;
		orePlace = null;
		oreOccluders.clear();
		oreMined.clear();
		oreMineIndex = 0;
		orePlaceIndex = 0;
		oreMineStall = 0;
		oreMineLastTarget = null;
	}

	private static void restoreSlot() {
		selectToolSlot();
		oreSavedSlot = -1;
	}

	private static void selectToolSlot() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && oreSavedSlot >= 0) {
			player.getInventory().setSelectedSlot(oreSavedSlot);
		}
	}

	private static boolean selectPlacementSlot(LocalPlayer player) {
		if (ScaffoldFilter.isPlacement(player.getInventory().getSelectedItem())) {
			return true;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (ScaffoldFilter.isPlacement(player.getInventory().getItem(slot))) {
				player.getInventory().setSelectedSlot(slot);
				return true;
			}
		}
		return false;
	}

	public static String lastError() {
		return lastError;
	}

	public static int blocksMined() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || planCells == null) {
			return 0;
		}
		int mined = 0;
		for (BlockPos cell : planCells) {
			if (client.level.getBlockState(cell).isAir()) {
				mined++;
			}
		}
		return mined;
	}

	public static int blocksTotal() {
		return planCells == null ? 0 : planCells.size();
	}

	public static BlockPos currentTarget() {
		return currentTarget;
	}

	private static boolean isStanding(LocalPlayer player) {
		return player.getEyeY() - player.getY() >= 1.0D;
	}

	private static boolean isOffPath(LocalPlayer player) {
		if (planCells == null || planCells.isEmpty()) {
			return false;
		}
		BlockPos pos = player.blockPosition();
		double bestDistance = Double.MAX_VALUE;
		int bestY = pos.getY();
		for (BlockPos cell : planCells) {
			double dx = cell.getX() - pos.getX();
			double dz = cell.getZ() - pos.getZ();
			double distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestY = cell.getY();
			}
		}
		return bestDistance > 6.25D || Math.abs(bestY - pos.getY()) > 1;
	}

	private static String dangerAt(Level level, BlockPos cell, Direction backward) {
		BlockState state = level.getBlockState(cell);
		if (!state.getFluidState().isEmpty()) {
			return "fluid";
		}
		if (state.getDestroySpeed(level, cell) < 0.0F) {
			return "unbreakable";
		}
		for (Direction dir : Direction.values()) {
			if (dir == backward || dir == backward.getOpposite()) {
				continue;
			}
			BlockPos neighbor = cell.relative(dir);
			if (!level.getBlockState(neighbor).isSolid()) {
				return "opening adjacent " + dir;
			}
		}
		BlockPos floor = cell.below();
		if (level.isLoaded(floor) && level.getBlockState(floor).getBlock() instanceof FallingBlock) {
			for (int dy = 2; dy <= FLOOR_SCAN_DEPTH; dy++) {
				BlockPos below = cell.below(dy);
				if (!level.isLoaded(below)) {
					break;
				}
				BlockState belowState = level.getBlockState(below);
				if (belowState.getBlock() instanceof FallingBlock) {
					continue;
				}
				if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
					return "falling floor";
				}
				break;
			}
		}
		return null;
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

	private static boolean isFacing(LocalPlayer player, Direction dir) {
		return Math.abs(Mth.wrapDegrees(player.getYRot() - dir.toYRot())) < FACE_TOLERANCE;
	}

	private static boolean isAdjacent(BlockPos a, BlockPos b) {
		int dx = Math.abs(a.getX() - b.getX());
		int dy = Math.abs(a.getY() - b.getY());
		int dz = Math.abs(a.getZ() - b.getZ());
		return dx + dy + dz == 1;
	}

	private static Direction directionFrom(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		int dz = to.getZ() - from.getZ();
		if (dy == 0) {
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
		} else if (dx == 0 && dz == 0) {
			if (dy == 1) {
				return Direction.UP;
			}
			if (dy == -1) {
				return Direction.DOWN;
			}
		}
		return null;
	}

	private static void message(String text) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.displayClientMessage(Component.literal(text), false);
		}
	}

	public static void resume() {
		if (lastPlan == null) {
			message("JHenry: nothing to resume");
			return;
		}
		start(lastPlan, true);
		if (mode == Mode.RUNNING && runs != null) {
			runIndex = findCurrentRun();
		}
	}

	private static int findCurrentRun() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || runs == null || runs.isEmpty()) {
			return 0;
		}
		BlockPos pos = player.blockPosition();

		for (int i = 0; i < runs.size(); i++) {
			for (BlockPos cell : runs.get(i).cells()) {
				if (cell.getX() == pos.getX() && cell.getZ() == pos.getZ()
						&& Math.abs(cell.getY() - pos.getY()) <= 2) {
					return i;
				}
			}
		}

		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < runs.size(); i++) {
			for (BlockPos cell : runs.get(i).cells()) {
				double distance = cell.distSqr(pos);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = i;
				}
			}
		}
		return best;
	}

	public static boolean hasResume() {
		return lastPlan != null;
	}

	public static boolean isRunning() {
		return mode == Mode.RUNNING;
	}

	public static String modeName() {
		return orePhase != OrePhase.NONE ? "ORE_MINING" : mode.name();
	}
}
