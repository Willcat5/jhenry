package willits.jhenry.mapping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record Partition(BlockPos min, BlockPos max, BlockPos start, Direction away, PartitionPattern pattern, int layer,
		MapConfig.Side half, int index) {
}
