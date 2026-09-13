package willits.jhenry.mapping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockSafety {

	private static final int FLOOR_SCAN_DEPTH = 32;

	private BlockSafety() {
	}

	public enum Verdict {
		SAFE,
		HAZARD,
		FALLING,
		UNBREAKABLE,
		UNLOADED
	}

	public static Verdict evaluate(Level level, BlockPos cell, Direction backward, boolean exemptForward) {
		if (!level.isLoaded(cell)) {
			return Verdict.UNLOADED;
		}

		BlockState state = level.getBlockState(cell);
		if (state.isAir()) {
			return Verdict.HAZARD;
		}
		if (!state.getFluidState().isEmpty()) {
			return Verdict.HAZARD;
		}
		if (state.getDestroySpeed(level, cell) < 0.0F) {
			return Verdict.UNBREAKABLE;
		}

		Direction forward = backward.getOpposite();
		for (Direction dir : Direction.values()) {
			if (dir == backward) {
				continue;
			}
			if (exemptForward && dir == forward) {
				continue;
			}

			BlockPos neighbor = cell.relative(dir);
			if (!level.isLoaded(neighbor)) {
				return Verdict.UNLOADED;
			}

			BlockState neighborState = level.getBlockState(neighbor);
			if (!neighborState.isSolid()) {
				return Verdict.HAZARD;
			}
		}

		BlockPos above = cell.above();
		if (!level.isLoaded(above)) {
			return Verdict.UNLOADED;
		}
		if (level.getBlockState(above).getBlock() instanceof FallingBlock) {
			return Verdict.FALLING;
		}

		BlockPos floor = cell.below();
		if (!level.isLoaded(floor)) {
			return Verdict.UNLOADED;
		}
		if (level.getBlockState(floor).getBlock() instanceof FallingBlock) {
			for (int dy = 2; dy <= FLOOR_SCAN_DEPTH; dy++) {
				BlockPos below = cell.below(dy);
				if (!level.isLoaded(below)) {
					return Verdict.UNLOADED;
				}
				BlockState belowState = level.getBlockState(below);
				if (belowState.getBlock() instanceof FallingBlock) {
					continue;
				}
				if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
					return Verdict.FALLING;
				}
				break;
			}
		}

		return Verdict.SAFE;
	}

	public static String describe(Level level, BlockPos cell, Direction backward, boolean exemptForward) {
		StringBuilder sb = new StringBuilder();
		if (!level.isLoaded(cell)) {
			return "unloaded " + cell.toShortString();
		}

		BlockState state = level.getBlockState(cell);
		sb.append("self=").append(blockId(state));
		if (state.isAir()) {
			sb.append("(AIR)");
		}
		if (!state.getFluidState().isEmpty()) {
			sb.append("(FLUID)");
		}

		Direction forward = backward.getOpposite();
		for (Direction dir : Direction.values()) {
			if (dir == backward) {
				continue;
			}
			if (exemptForward && dir == forward) {
				continue;
			}
			BlockPos neighbor = cell.relative(dir);
			if (!level.isLoaded(neighbor)) {
				sb.append(" ").append(dir).append("=unloaded");
				continue;
			}
			BlockState ns = level.getBlockState(neighbor);
			if (ns.isAir()) {
				sb.append(" ").append(dir).append("=").append(blockId(ns)).append("(AIR)");
			} else if (!ns.getFluidState().isEmpty()) {
				sb.append(" ").append(dir).append("=").append(blockId(ns)).append("(FLUID)");
			}
		}

		BlockState above = level.getBlockState(cell.above());
		if (above.getBlock() instanceof FallingBlock) {
			sb.append(" above=").append(blockId(above)).append("(FALLING)");
		}
		return sb.toString();
	}

	private static String blockId(BlockState state) {
		return state.getBlock().getName().getString();
	}
}
