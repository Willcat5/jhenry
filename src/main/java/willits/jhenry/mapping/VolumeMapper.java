package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class VolumeMapper {

	private VolumeMapper() {
	}

	public static VolumePlan map(Level level, VolumeMark mark, MapConfig config) {
		return map(level, mark, config.volumeSplitWidth);
	}

	public static VolumePlan map(Level level, VolumeMark mark, int splitWidth) {
		List<Partition> partitions = VolumePartitioner.partition(mark, splitWidth);
		int width = VolumePartitioner.width(mark);
		int height = VolumePartitioner.height(mark);
		int length = Math.max(1, mark.length());
		String detail = "w=" + width + " h=" + height + " l=" + length + " parts=" + partitions.size();
		return new VolumePlan(mark, partitions, width, height, length, detail);
	}

	public static List<BlockPos> cells(Partition partition) {
		Direction away = partition.away();
		Direction width = away.getClockWise();
		BlockPos min = partition.min();
		BlockPos max = partition.max();

		int wCount = Math.abs(dot(max, width) - dot(min, width)) + 1;
		int dCount = Math.abs(dot(max, away) - dot(min, away)) + 1;
		int yMin = min.getY();
		int yCount = max.getY() - min.getY() + 1;

		List<BlockPos> cells = new ArrayList<>(wCount * dCount * yCount);
		for (int stepIndex = 0; stepIndex < wCount; stepIndex++) {
			int column = wCount - 1 - stepIndex;
			boolean forward = stepIndex % 2 == 0;
			for (int step = 0; step < dCount; step++) {
				int depth = forward ? step : dCount - 1 - step;
				BlockPos base = min.relative(width, column).relative(away, depth);
				for (int j = 0; j < yCount; j++) {
					cells.add(new BlockPos(base.getX(), yMin + j, base.getZ()));
				}
			}
		}
		return cells;
	}

	public static PartitionPlan plan(Level level, Partition partition) {
		List<BlockPos> cells = new ArrayList<>();
		for (BlockPos cell : cells(partition)) {
			if (level.isLoaded(cell) && level.getBlockState(cell).isAir()) {
				continue;
			}
			cells.add(cell);
		}
		return new PartitionPlan(partition, cells, StopReason.NONE, "");
	}

	private static int dot(BlockPos pos, Direction dir) {
		return pos.getX() * dir.getStepX() + pos.getZ() * dir.getStepZ();
	}
}
