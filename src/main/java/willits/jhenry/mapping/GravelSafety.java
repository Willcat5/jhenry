package willits.jhenry.mapping;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class GravelSafety {

	private static final int CAP = 64;
	private static final int COLUMN_LIMIT = 64;

	public record Result(BlockSafety.Verdict verdict, String detail) {
	}

	private static final Result SAFE = new Result(BlockSafety.Verdict.SAFE, "");

	private GravelSafety() {
	}

	public static Result scan(Level level, BlockPos pathCell, Set<BlockPos> allowedAir) {
		Set<BlockPos> body = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();

		if (level.getBlockState(pathCell).getBlock() instanceof FallingBlock) {
			seed(body, queue, pathCell);
		}
		BlockPos cursor = pathCell.above();
		for (int i = 0; i < COLUMN_LIMIT; i++) {
			if (!level.isLoaded(cursor)) {
				return unloaded(cursor);
			}
			if (!(level.getBlockState(cursor).getBlock() instanceof FallingBlock)) {
				break;
			}
			seed(body, queue, cursor);
			cursor = cursor.above();
		}

		if (body.isEmpty()) {
			return SAFE;
		}

		while (!queue.isEmpty() && body.size() < CAP) {
			BlockPos p = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos neighbor = p.relative(dir);
				if (body.contains(neighbor)) {
					continue;
				}
				if (!level.isLoaded(neighbor)) {
					return unloaded(neighbor);
				}
				if (level.getBlockState(neighbor).getBlock() instanceof FallingBlock) {
					seed(body, queue, neighbor);
				}
			}
		}

		for (BlockPos p : body) {
			for (Direction dir : Direction.values()) {
				BlockPos neighbor = p.relative(dir);
				if (!level.isLoaded(neighbor)) {
					return unloaded(neighbor);
				}
				BlockState neighborState = level.getBlockState(neighbor);
				if (!neighborState.getFluidState().isEmpty()) {
					return new Result(BlockSafety.Verdict.HAZARD, "exposes "
							+ neighborState.getBlock().getName().getString() + " at " + neighbor.toShortString());
				}
				if (neighborState.isAir() && !allowedAir.contains(neighbor)) {
					return new Result(BlockSafety.Verdict.HAZARD, "exposes air at " + neighbor.toShortString());
				}
			}

			BlockPos below = p.below();
			if (!level.isLoaded(below)) {
				return unloaded(below);
			}
			BlockState belowState = level.getBlockState(below);
			if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
				return new Result(BlockSafety.Verdict.HAZARD, "unsupported gravel at " + p.toShortString());
			}
		}

		return SAFE;
	}

	private static void seed(Set<BlockPos> body, ArrayDeque<BlockPos> queue, BlockPos pos) {
		BlockPos immutable = pos.immutable();
		if (body.add(immutable)) {
			queue.add(immutable);
		}
	}

	private static Result unloaded(BlockPos pos) {
		return new Result(BlockSafety.Verdict.UNLOADED, "unloaded " + pos.toShortString());
	}
}
