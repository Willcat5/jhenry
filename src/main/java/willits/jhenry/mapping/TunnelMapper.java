package willits.jhenry.mapping;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import willits.jhenry.JHenry;

public final class TunnelMapper {

	private static final int SIDESTEP = 4;
	private static final int ORIGIN_BFS_CAP = 512;
	private static final int ORIGIN_LATERAL_LIMIT = 96;

	private TunnelMapper() {
	}

	public static TunnelPlan map(Level level, TunnelMark mark, MapConfig config) {
		Direction dir = mark.facing().getOpposite();
		Direction lateral = config.side == MapConfig.Side.LEFT
				? dir.getCounterClockWise()
				: dir.getClockWise();

		Set<BlockPos> originOpen = collectOriginOpen(level, mark, dir);

		int capL = Math.max(0, (config.maxBlocks - SIDESTEP) / 2);

		int naturalL = 0;
		StopReason forwardReason = StopReason.MAX_DISTANCE;
		for (int i = 1; i <= capL; i++) {
			BlockPos cell = mark.entrance().relative(dir, i);
			BlockSafety.Verdict verdict = BlockSafety.evaluate(level, cell, dir.getOpposite(), false);
			if (verdict != BlockSafety.Verdict.SAFE) {
				forwardReason = reasonFor(verdict);
				break;
			}
			naturalL = i;
		}

		int startL = Math.min(naturalL, capL);
		Attempt first = null;
		Attempt last = null;
		int fails = 0;
		for (int length = startL; length >= 1; length--) {
			Attempt attempt = tryBuild(level, mark.entrance(), dir, lateral, length, originOpen);
			if (attempt.valid) {
				StopReason reason = attempt.reason != null ? attempt.reason : forwardReason;
				BlockPos stop = mark.entrance().relative(dir, length + 1);
				TunnelPlan plan = new TunnelPlan(mark, dir, length, attempt.cells, reason, attempt.cells.size(),
						"forward=" + forwardReason, stop, Segment.OUTWARD, attempt.returnStop);
				JHenry.LOGGER.info("map {} dir={} lateral={} capL={} naturalL={} forward={} -> len={} blocks={} returnStop={}",
						mark.entrance().toShortString(), dir, lateral, capL, naturalL, forwardReason, length,
						plan.blockCount(), attempt.returnStop == null ? "base" : attempt.returnStop.toShortString());
				return plan;
			}
			fails++;
			if (first == null) {
				first = attempt;
			}
			last = attempt;
		}

		BlockPos stop = first != null ? first.failPos : mark.entrance().relative(dir, 1);
		Segment stopSegment = first != null ? first.failSegment : Segment.OUTWARD;
		String firstDetail = first != null ? first.detail : null;
		String lastDetail = last != null ? last.detail : null;
		JHenry.LOGGER.info("map {} dir={} lateral={} capL={} naturalL={} forward={} FAILED fails={} first=[{}] last=[{}]",
				mark.entrance().toShortString(), dir, lateral, capL, naturalL, forwardReason, fails, firstDetail, lastDetail);
		String detail = firstDetail != null ? firstDetail
				: "naturalL=0 forward=" + forwardReason + " firstCell=" + mark.entrance().relative(dir, 1).toShortString();
		return new TunnelPlan(mark, dir, 0, List.of(), StopReason.NO_VALID_LENGTH, 0, detail, stop, stopSegment, null);
	}

	private static Set<BlockPos> collectOriginOpen(Level level, TunnelMark mark, Direction dir) {
		Set<BlockPos> open = new HashSet<>();
		BlockPos start = mark.entrance().relative(dir.getOpposite());
		if (!level.isLoaded(start) || !level.getBlockState(start).isAir()) {
			return open;
		}

		Direction.Axis axis = dir.getAxis();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		open.add(start);

		while (!queue.isEmpty() && open.size() < ORIGIN_BFS_CAP) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (open.contains(n) || !level.isLoaded(n)) {
					continue;
				}
				int along = axis == Direction.Axis.X
						? (n.getX() - mark.entrance().getX()) * dir.getStepX()
						: (n.getZ() - mark.entrance().getZ()) * dir.getStepZ();
				if (along < -2 || along > 1) {
					continue;
				}
				int dy = n.getY() - mark.entrance().getY();
				if (dy < 0 || dy > 1) {
					continue;
				}
				int lateral = axis == Direction.Axis.X
						? n.getZ() - mark.entrance().getZ()
						: n.getX() - mark.entrance().getX();
				if (Math.abs(lateral) > ORIGIN_LATERAL_LIMIT) {
					continue;
				}
				if (level.getBlockState(n).isAir()) {
					open.add(n);
					queue.add(n);
				}
			}
		}
		return open;
	}

	private static Attempt tryBuild(Level level, BlockPos entrance, Direction dir, Direction lateral, int length,
			Set<BlockPos> originOpen) {
		List<PlannedCell> cells = new ArrayList<>();
		Set<BlockPos> planned = new HashSet<>();

		BlockPos cursor = entrance;
		for (int i = 0; i < length; i++) {
			cursor = cursor.relative(dir);
			BlockSafety.Verdict verdict = BlockSafety.evaluate(level, cursor, dir.getOpposite(), false);
			if (verdict != BlockSafety.Verdict.SAFE) {
				return Attempt.blocked(reasonFor(verdict), "OUTWARD " + cursor.toShortString() + " " + verdict
						+ " [" + BlockSafety.describe(level, cursor, dir.getOpposite(), false) + "]",
						cursor.immutable(), Segment.OUTWARD);
			}
			planned.add(cursor.immutable());
			cells.add(new PlannedCell(cursor.immutable(), Segment.OUTWARD));
		}

		for (int i = 0; i < SIDESTEP; i++) {
			cursor = cursor.relative(lateral);
			BlockSafety.Verdict verdict = BlockSafety.evaluate(level, cursor, lateral.getOpposite(), false);
			if (verdict != BlockSafety.Verdict.SAFE) {
				return Attempt.blocked(segmentReason(verdict, StopReason.SIDESTEP_BLOCKED),
						"SIDESTEP " + cursor.toShortString() + " " + verdict
								+ " [" + BlockSafety.describe(level, cursor, lateral.getOpposite(), false) + "]",
						cursor.immutable(), Segment.SIDESTEP);
			}
			planned.add(cursor.immutable());
			cells.add(new PlannedCell(cursor.immutable(), Segment.SIDESTEP));
		}

		BlockPos returnStop = null;
		for (int i = 0; i < length; i++) {
			BlockPos next = cursor.relative(dir.getOpposite());

			if (originOpen.contains(next)) {
				break;
			}

			boolean forwardIsOrigin = originOpen.contains(next.relative(dir.getOpposite()));
			BlockSafety.Verdict verdict = BlockSafety.evaluate(level, next, dir, forwardIsOrigin);
			if (verdict != BlockSafety.Verdict.SAFE) {
				returnStop = next.immutable();
				Attempt cut = cutOver(level, cells, planned, cursor, dir, lateral);
				if (cut != null) {
					return cut;
				}
				break;
			}

			cursor = next;
			planned.add(cursor.immutable());
			cells.add(new PlannedCell(cursor.immutable(), Segment.RETURN));
		}

		return Attempt.valid(cells, returnStop);
	}

	private static Attempt cutOver(Level level, List<PlannedCell> cells, Set<BlockPos> planned, BlockPos from,
			Direction dir, Direction lateral) {
		BlockPos cut = from;
		for (int j = 0; j < SIDESTEP; j++) {
			cut = cut.relative(lateral.getOpposite());
			if (planned.contains(cut)) {
				return null;
			}
			BlockSafety.Verdict verdict = BlockSafety.evaluate(level, cut, lateral, false);
			if (verdict != BlockSafety.Verdict.SAFE) {
				return Attempt.blocked(StopReason.RETURN_BLOCKED,
						"RECONNECT " + cut.toShortString() + " " + verdict
								+ " [" + BlockSafety.describe(level, cut, lateral, false) + "]",
						cut.immutable(), Segment.RECONNECT);
			}
			planned.add(cut.immutable());
			cells.add(new PlannedCell(cut.immutable(), Segment.RECONNECT));
		}
		return null;
	}

	private static StopReason segmentReason(BlockSafety.Verdict verdict, StopReason blocked) {
		return verdict == BlockSafety.Verdict.UNLOADED ? StopReason.UNLOADED : blocked;
	}

	private static StopReason reasonFor(BlockSafety.Verdict verdict) {
		return switch (verdict) {
			case FALLING -> StopReason.FALLING_BLOCK;
			case UNBREAKABLE -> StopReason.UNBREAKABLE;
			case UNLOADED -> StopReason.UNLOADED;
			default -> StopReason.INVALID_HAZARD;
		};
	}

	private static final class Attempt {
		final boolean valid;
		final List<PlannedCell> cells;
		final StopReason reason;
		final String detail;
		final BlockPos failPos;
		final Segment failSegment;
		final BlockPos returnStop;

		private Attempt(boolean valid, List<PlannedCell> cells, StopReason reason, String detail,
				BlockPos failPos, Segment failSegment, BlockPos returnStop) {
			this.valid = valid;
			this.cells = cells;
			this.reason = reason;
			this.detail = detail;
			this.failPos = failPos;
			this.failSegment = failSegment;
			this.returnStop = returnStop;
		}

		static Attempt valid(List<PlannedCell> cells, BlockPos returnStop) {
			return new Attempt(true, cells, null, "", null, null, returnStop);
		}

		static Attempt blocked(StopReason reason, String detail, BlockPos failPos, Segment failSegment) {
			return new Attempt(false, List.of(), reason, detail, failPos, failSegment, null);
		}
	}
}
