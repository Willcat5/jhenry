package willits.jhenry.mapping;

import java.util.List;

import net.minecraft.core.BlockPos;

public record PartitionPlan(Partition partition, List<BlockPos> cells, StopReason stop, String detail) {
}
