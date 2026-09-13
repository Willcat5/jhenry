package willits.jhenry.mapping;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record TunnelPlan(TunnelMark mark, Direction direction, int length, List<PlannedCell> cells, StopReason stopReason,
		int blockCount, String detail, BlockPos stopPos, Segment stopSegment, BlockPos returnStopPos) {
}
