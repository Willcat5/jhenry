package willits.jhenry.client.dig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willits.jhenry.client.look.LookController;
import willits.jhenry.mapping.BlockSafety;
import willits.jhenry.mapping.GravelSafety;
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
	private static final int GRAVEL_STALL_LIMIT = 200;
	private static final int PEEK_STALL_LIMIT = 200;
	private static final int PLACEMENT_WAIT_LIMIT = 60;
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

	private enum GravelPhase {
		NONE,
		SCAN,
		DIG,
		CAP
	}

	private enum PeekPhase {
		NONE,
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

	private static GravelPhase gravelPhase = GravelPhase.NONE;
	private static BlockPos gravelPath;
	private static Direction gravelDir;
	private static int gravelDigStall;
	private static int gravelSafetyTimer;
	private static Block gravelLastBlock;
	private static int gravelSavedSlot = -1;
	private static final Set<BlockPos> gravelAir = new HashSet<>();

	private static PeekPhase peekPhase = PeekPhase.NONE;
	private static BlockPos peekTarget;
	private static List<BlockPos> peekTargets;
	private static final Set<BlockPos> peekDone = new HashSet<>();
	private static int peekStall;
	private static Block peekLastBlock;
	private static int peekSavedSlot = -1;

	private static int placementWaits;
	private static boolean placementRefillPending;

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
		resetGravelTask();
		resetPeekTask();
		placementWaits = 0;
		placementRefillPending = false;
		peekTargets = buildPeekTargets(plan);
		mode = Mode.RUNNING;
	}

	public static void stop() {
		BlockBreaker.stop();
		MoveController.stop();
		LookController.stop();
		resetOreTask();
		resetGravelTask();
		resetPeekTask();
		placementWaits = 0;
		placementRefillPending = false;
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

		if (gravelPhase != GravelPhase.NONE) {
			tickGravelTask(client, player);
			return;
		}

		if (peekPhase != PeekPhase.NONE) {
			tickPeekTask(client, player);
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

		if (peekPhase == PeekPhase.NONE && startPeekOpportunity(client, player)) {
			return;
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

			if (MarkManager.config().handleGravel && isGravelColumn(client, nearest)) {
				approachGravel(client, player, run, nearest);
				return;
			}

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

			ToolSelector.hold(player, client.level.getBlockState(nearest));

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
		resetGravelTask();
		resetPeekTask();
		if (lastPlan != null) {
			MarkManager.plans().remove(lastPlan);
			lastPlan = null;
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
		resetGravelTask();
		resetPeekTask();
		mode = Mode.PAUSED;
		lastError = reason;
		message("JHenry: paused - " + reason);
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
		if (mineable.size() < vein.size()) {
			pause("ore vein extends past tunnel [" + veinOres(client, vein) + "]");
			return;
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

	private static String veinOres(Minecraft client, List<BlockPos> vein) {
		Set<Block> types = new LinkedHashSet<>();
		for (BlockPos ore : vein) {
			types.add(client.level.getBlockState(ore).getBlock());
		}
		StringBuilder message = new StringBuilder();
		for (Block type : types) {
			if (message.length() > 0) {
				message.append("/");
			}
			message.append(type.getName().getString());
		}
		return message.toString();
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
			if (!hitPos.equals(cell) && inScope(hitPos) && !client.level.getBlockState(hitPos).isAir()) {
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
			if (++placementWaits > PLACEMENT_WAIT_LIMIT) {
				fail("no placement block available");
			}
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

	private static void approachGravel(Minecraft client, LocalPlayer player, Run run, BlockPos path) {
		if (!isAdjacent(player.blockPosition(), path)) {
			BlockBreaker.releaseAttack();
			BlockPos staging = path.relative(run.dir().getOpposite());
			if (!MoveController.atTarget(player, staging) && !MoveController.isMoving()) {
				MoveController.moveTo(staging);
			}
			return;
		}
		startGravelTask(path, run.dir());
	}

	private static boolean isGravelColumn(Minecraft client, BlockPos cell) {
		if (client.level.getBlockState(cell).getBlock() instanceof FallingBlock) {
			return true;
		}
		if (!client.level.isLoaded(cell.above())) {
			return false;
		}
		return client.level.getBlockState(cell.above()).getBlock() instanceof FallingBlock;
	}

	private static void startGravelTask(BlockPos path, Direction dir) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			gravelSavedSlot = player.getInventory().getSelectedSlot();
		}
		gravelPath = path.immutable();
		gravelDir = dir;
		gravelDigStall = 0;
		gravelSafetyTimer = 0;
		gravelLastBlock = null;
		gravelPhase = GravelPhase.SCAN;
		BlockBreaker.stop();
		MoveController.stop();
		LookController.stop();
	}

	private static void tickGravelTask(Minecraft client, LocalPlayer player) {
		switch (gravelPhase) {
			case SCAN -> gravelScan(client, player);
			case DIG -> tickGravelDig(client, player);
			case CAP -> tickGravelCap(client, player);
			default -> {
			}
		}
	}

	private static void gravelScan(Minecraft client, LocalPlayer player) {
		if (gravelPath == null || !client.level.isLoaded(gravelPath)) {
			endGravelColumn(client);
			return;
		}

		String safety = checkSafety(player);
		if (safety != null) {
			fail(safety);
			return;
		}

		boolean pathFalling = client.level.getBlockState(gravelPath).getBlock() instanceof FallingBlock;
		BlockPos above = gravelPath.above();
		boolean aboveFalling = client.level.isLoaded(above)
				&& client.level.getBlockState(above).getBlock() instanceof FallingBlock;
		if (!pathFalling && !aboveFalling) {
			endGravelColumn(client);
			return;
		}

		GravelSafety.Result result = GravelSafety.scan(client.level, gravelPath, gravelAllowedAir(client, player));
		if (result.verdict() != BlockSafety.Verdict.SAFE) {
			pause("gravel pocket " + result.detail());
			return;
		}

		gravelLastBlock = null;
		gravelDigStall = 0;
		gravelSafetyTimer = 0;
		gravelPhase = GravelPhase.DIG;
	}

	private static void tickGravelDig(Minecraft client, LocalPlayer player) {
		if (gravelPath == null || !client.level.isLoaded(gravelPath)) {
			endGravelColumn(client);
			return;
		}

		if (++gravelSafetyTimer >= MINE_CHECK_INTERVAL) {
			gravelSafetyTimer = 0;
			String safety = checkSafety(player);
			if (safety != null) {
				fail(safety);
				return;
			}
		}

		BlockState state = client.level.getBlockState(gravelPath);
		Block current = state.getBlock();
		if (current != gravelLastBlock) {
			gravelLastBlock = current;
			gravelDigStall = 0;
		} else if (++gravelDigStall > GRAVEL_STALL_LIMIT) {
			fail("gravel blocked at " + gravelPath.toShortString());
			return;
		}

		if (state.isAir()) {
			BlockPos above = gravelPath.above();
			boolean aboveFalling = client.level.isLoaded(above)
					&& client.level.getBlockState(above).getBlock() instanceof FallingBlock;
			BlockBreaker.stop();
			if (!aboveFalling && !gravelFallingNear(client, gravelPath)) {
				recordGravelShaft(client);
				gravelPhase = GravelPhase.CAP;
			}
			return;
		}

		if (!state.getFluidState().isEmpty()) {
			fail("gravel exposed fluid at " + gravelPath.toShortString());
			return;
		}
		if (state.getDestroySpeed(client.level, gravelPath) < 0.0F) {
			fail("gravel blocked by unbreakable at " + gravelPath.toShortString());
			return;
		}

		if (BlockBreaker.target() == null || !BlockBreaker.target().equals(gravelPath)) {
			BlockBreaker.breakBlock(gravelPath);
		}
	}

	private static void tickGravelCap(Minecraft client, LocalPlayer player) {
		if (gravelPath == null || !client.level.isLoaded(gravelPath)) {
			endGravelColumn(client);
			return;
		}

		if (!isAdjacent(player.blockPosition(), gravelPath)) {
			BlockPos staging = gravelPath.relative(gravelDir != null ? gravelDir.getOpposite() : Direction.UP);
			if (!MoveController.atTarget(player, staging) && !MoveController.isMoving()) {
				MoveController.moveTo(staging);
			}
			return;
		}

		BlockPos cap = gravelPath.above();
		if (!client.level.getBlockState(cap).isAir()) {
			endGravelColumn(client);
			return;
		}

		if (!selectPlacementSlot(player)) {
			if (++placementWaits > PLACEMENT_WAIT_LIMIT) {
				fail("no placement block available");
			}
			return;
		}

		if (!BlockPlacer.isPlacing() || !cap.equals(BlockPlacer.target())) {
			BlockPlacer.place(cap);
		}

		BlockPlacer.Status status = BlockPlacer.tick();
		if (status == BlockPlacer.Status.DONE) {
			endGravelColumn(client);
		} else if (status == BlockPlacer.Status.FAILED) {
			fail("could not cap gravel at " + cap.toShortString());
		}
	}

	private static boolean gravelFallingNear(Minecraft client, BlockPos path) {
		AABB box = new AABB(
				path.getX() - 1.0D, path.getY() - 1.0D, path.getZ() - 1.0D,
				path.getX() + 2.0D, path.getY() + 4.0D, path.getZ() + 2.0D);
		return !client.level.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty();
	}

	private static void recordGravelShaft(Minecraft client) {
		if (gravelPath == null || client.level == null) {
			return;
		}
		gravelAir.add(gravelPath);
		BlockPos up = gravelPath.above();
		while (client.level.isLoaded(up) && client.level.getBlockState(up).isAir()) {
			gravelAir.add(up.immutable());
			up = up.above();
		}
	}

	private static void endGravelColumn(Minecraft client) {
		BlockBreaker.stop();
		BlockPlacer.stop();
		MoveController.stop();
		LookController.stop();
		selectGravelTool();
		gravelSavedSlot = -1;
		recordGravelShaft(client);
		gravelPhase = GravelPhase.NONE;
		gravelPath = null;
		gravelDir = null;
		gravelDigStall = 0;
		gravelSafetyTimer = 0;
		gravelLastBlock = null;
	}

	private static void resetGravelTask() {
		BlockPlacer.stop();
		selectGravelTool();
		gravelSavedSlot = -1;
		gravelPhase = GravelPhase.NONE;
		gravelPath = null;
		gravelDir = null;
		gravelDigStall = 0;
		gravelSafetyTimer = 0;
		gravelLastBlock = null;
		gravelAir.clear();
	}

	private static Set<BlockPos> gravelAllowedAir(Minecraft client, LocalPlayer player) {
		Set<BlockPos> allowed = new HashSet<>(gravelAir);
		if (planCells != null) {
			for (BlockPos cell : planCells) {
				if (client.level.isLoaded(cell) && client.level.getBlockState(cell).isAir()) {
					allowed.add(cell);
				}
			}
		}
		if (player != null) {
			allowed.add(player.blockPosition());
		}
		return allowed;
	}

	private static void selectGravelTool() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && gravelSavedSlot >= 0) {
			player.getInventory().setSelectedSlot(gravelSavedSlot);
		}
	}

	private static List<BlockPos> buildPeekTargets(TunnelPlan plan) {
		List<BlockPos> targets = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !MiningSettings.peek()) {
			return targets;
		}
		List<BlockPos> candidates = new ArrayList<>();
		if (plan.stopPos() != null && plan.stopReason().isHazard()) {
			candidates.add(plan.stopPos());
		}
		if (plan.returnStopPos() != null) {
			candidates.add(plan.returnStopPos());
		}
		for (BlockPos pos : candidates) {
			if (peekEligible(client.level, pos) && !targets.contains(pos)) {
				targets.add(pos.immutable());
			}
		}
		return targets;
	}

	private static boolean peekEligible(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = pos.relative(dir);
			if (!level.isLoaded(neighbor)) {
				continue;
			}
			if (!level.getBlockState(neighbor).isAir()) {
				continue;
			}
			if (planCells != null && planCells.contains(neighbor)) {
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean hasPlacement(LocalPlayer player) {
		for (int slot = 0; slot < 36; slot++) {
			if (ScaffoldFilter.isPlacement(player.getInventory().getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	private static boolean startPeekOpportunity(Minecraft client, LocalPlayer player) {
		if (peekTargets == null || peekTargets.isEmpty()) {
			return false;
		}
		for (BlockPos target : peekTargets) {
			if (peekDone.contains(target)) {
				continue;
			}
			if (!peekEligible(client.level, target)) {
				peekDone.add(target);
				continue;
			}
			if (!withinReach(player, target) || !canTarget(client, player, target)) {
				continue;
			}
			if (!hasPlacement(player)) {
				peekDone.add(target);
				continue;
			}
			startPeekTask(player, target);
			return true;
		}
		return false;
	}

	private static boolean withinReach(LocalPlayer player, BlockPos target) {
		return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= 16.0D;
	}

	private static boolean canTarget(Minecraft client, LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = client.level.clip(new ClipContext(eye, Vec3.atCenterOf(target),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
	}

	private static BlockPos adjacentPlanCell(BlockPos target) {
		if (planCells == null) {
			return null;
		}
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = target.relative(dir);
			if (planCells.contains(neighbor)) {
				return neighbor;
			}
		}
		return null;
	}

	private static void startPeekTask(LocalPlayer player, BlockPos target) {
		if (player != null) {
			peekSavedSlot = player.getInventory().getSelectedSlot();
		}
		peekTarget = target.immutable();
		peekStall = 0;
		peekLastBlock = null;
		peekPhase = PeekPhase.MINE;
		BlockBreaker.stop();
		LookController.stop();
		BlockPos approach = adjacentPlanCell(peekTarget);
		if (player != null && approach != null && !MoveController.atTarget(player, approach)) {
			MoveController.moveTo(approach);
		}
	}

	private static void tickPeekTask(Minecraft client, LocalPlayer player) {
		switch (peekPhase) {
			case MINE -> tickPeekMine(client, player);
			case PLACE -> tickPeekPlace(client, player);
			default -> {
			}
		}
	}

	private static void tickPeekMine(Minecraft client, LocalPlayer player) {
		if (peekTarget == null || !client.level.isLoaded(peekTarget)) {
			endPeekTask(player);
			return;
		}
		BlockState state = client.level.getBlockState(peekTarget);
		Block current = state.getBlock();
		if (current != peekLastBlock) {
			peekLastBlock = current;
			peekStall = 0;
		} else if (++peekStall > PEEK_STALL_LIMIT) {
			endPeekTask(player);
			return;
		}

		if (state.isAir()) {
			BlockBreaker.stop();
			MoveController.stop();
			peekPhase = PeekPhase.PLACE;
			return;
		}

		if (!state.getFluidState().isEmpty() || state.getDestroySpeed(client.level, peekTarget) < 0.0F) {
			endPeekTask(player);
			return;
		}

		if (BlockBreaker.target() == null || !BlockBreaker.target().equals(peekTarget)) {
			BlockBreaker.breakBlock(peekTarget);
		}
	}

	private static void tickPeekPlace(Minecraft client, LocalPlayer player) {
		if (peekTarget == null || !client.level.getBlockState(peekTarget).isAir()) {
			endPeekTask(player);
			return;
		}
		if (!withinReach(player, peekTarget)) {
			BlockPos approach = adjacentPlanCell(peekTarget);
			if (approach == null) {
				endPeekTask(player);
				return;
			}
			if (!MoveController.atTarget(player, approach) && !MoveController.isMoving()) {
				MoveController.moveTo(approach);
			}
			return;
		}
		if (!selectPlacementSlot(player)) {
			if (++placementWaits > PLACEMENT_WAIT_LIMIT) {
				endPeekTask(player);
			}
			return;
		}
		if (!BlockPlacer.isPlacing() || !peekTarget.equals(BlockPlacer.target())) {
			BlockPlacer.place(peekTarget);
		}
		BlockPlacer.Status status = BlockPlacer.tick();
		if (status == BlockPlacer.Status.DONE || status == BlockPlacer.Status.FAILED) {
			endPeekTask(player);
		}
	}

	private static void endPeekTask(LocalPlayer player) {
		BlockBreaker.stop();
		BlockPlacer.stop();
		MoveController.stop();
		if (player != null && peekSavedSlot >= 0) {
			player.getInventory().setSelectedSlot(peekSavedSlot);
		}
		peekSavedSlot = -1;
		if (peekTarget != null) {
			peekDone.add(peekTarget);
		}
		peekTarget = null;
		peekStall = 0;
		peekLastBlock = null;
		peekPhase = PeekPhase.NONE;
	}

	private static void resetPeekTask() {
		BlockBreaker.stop();
		BlockPlacer.stop();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && peekSavedSlot >= 0) {
			player.getInventory().setSelectedSlot(peekSavedSlot);
		}
		peekSavedSlot = -1;
		peekPhase = PeekPhase.NONE;
		peekTarget = null;
		peekTargets = null;
		peekDone.clear();
		peekStall = 0;
		peekLastBlock = null;
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
			placementRefillPending = false;
			placementWaits = 0;
			return true;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (ScaffoldFilter.isPlacement(player.getInventory().getItem(slot))) {
				player.getInventory().setSelectedSlot(slot);
				placementRefillPending = false;
				placementWaits = 0;
				return true;
			}
		}
		attemptPlacementRefill(player);
		return false;
	}

	private static void attemptPlacementRefill(LocalPlayer player) {
		if (placementRefillPending) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		int source = -1;
		for (int slot = 9; slot < 36; slot++) {
			if (ScaffoldFilter.isPlacement(inventory.getItem(slot))) {
				source = slot;
				break;
			}
		}
		if (source < 0) {
			return;
		}
		int target = -1;
		for (int slot = 0; slot < 9; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				target = slot;
				break;
			}
		}
		if (target < 0) {
			return;
		}
		client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, source, target,
				ClickType.SWAP, player);
		placementRefillPending = true;
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

	public static void notifyDamage() {
		if (!MiningSettings.pauseOnDamage()) {
			return;
		}
		if (mode == Mode.RUNNING) {
			pause("took damage");
		} else {
			message("JHenry: took damage");
		}
	}

	public static String modeName() {
		if (orePhase != OrePhase.NONE) {
			return "ORE_MINING";
		}
		if (gravelPhase != GravelPhase.NONE) {
			return "GRAVEL";
		}
		if (peekPhase != PeekPhase.NONE) {
			return "PEEK";
		}
		return mode.name();
	}
}
