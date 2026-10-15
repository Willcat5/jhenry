package willits.jhenry.mapping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record VolumeMark(BlockPos a, BlockPos b, Direction away, int length, ResourceKey<Level> dimension) {
}
