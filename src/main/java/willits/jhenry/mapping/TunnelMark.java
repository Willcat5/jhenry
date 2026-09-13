package willits.jhenry.mapping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record TunnelMark(BlockPos entrance, Direction facing, boolean open, ResourceKey<Level> dimension) {
}
