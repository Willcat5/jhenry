package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class VolumePartitioner {

	private VolumePartitioner() {
	}

	public static List<Partition> partition(VolumeMark mark, int splitWidth) {
		Direction away = mark.away();
		Direction width = away.getClockWise();

		BlockPos a = mark.a();
		BlockPos b = mark.b();

		int aw = dot(a, width);
		int bw = dot(b, width);
		int ad = dot(a, away);
		int bd = dot(b, away);

		int w0 = Math.min(aw, bw);
		int d0 = Math.min(ad, bd);
		int y0 = Math.min(a.getY(), b.getY());

		int w = Math.abs(aw - bw) + 1;
		int h = Math.abs(a.getY() - b.getY()) + 1;
		int length = Math.max(1, mark.length());

		BlockPos base = a.relative(width, w0 - aw).relative(away, d0 - ad);
		base = new BlockPos(base.getX(), y0, base.getZ());

		int rightWidth = (w + 1) / 2;
		int leftWidth = w / 2;
		if (w < splitWidth) {
			rightWidth = w;
			leftWidth = 0;
		}
		int layers = (h + 1) / 2;

		List<Partition> partitions = new ArrayList<>();
		int index = 0;
		for (MapConfig.Side half : new MapConfig.Side[] { MapConfig.Side.LEFT, MapConfig.Side.RIGHT }) {
			int iStart = half == MapConfig.Side.RIGHT ? leftWidth : 0;
			int iCount = half == MapConfig.Side.RIGHT ? rightWidth : leftWidth;
			if (iCount <= 0) {
				continue;
			}
			for (int layer = 0; layer < layers; layer++) {
				int jStart = layer * 2;
				int jCount = Math.min(2, h - jStart);
				BlockPos near = base.relative(width, iStart);
				BlockPos far = base.relative(width, iStart + iCount - 1).relative(away, length - 1);
				BlockPos min = new BlockPos(near.getX(), y0 + jStart, near.getZ());
				BlockPos max = new BlockPos(far.getX(), y0 + jStart + jCount - 1, far.getZ());
				BlockPos enter = base.relative(width, iStart + iCount - 1);
				BlockPos start = new BlockPos(enter.getX(), y0, enter.getZ());
				PartitionPattern pattern = layer == 0 ? PartitionPattern.FLOOR : PartitionPattern.UPPER;
				partitions.add(new Partition(min, max, start, away, pattern, layer, half, index++));
			}
		}
		return partitions;
	}

	public static int width(VolumeMark mark) {
		return widthBetween(mark.a(), mark.b(), mark.away());
	}

	public static int height(VolumeMark mark) {
		return heightBetween(mark.a(), mark.b());
	}

	public static int widthBetween(BlockPos a, BlockPos b, Direction away) {
		Direction width = away.getClockWise();
		return Math.abs(dot(a, width) - dot(b, width)) + 1;
	}

	public static int heightBetween(BlockPos a, BlockPos b) {
		return Math.abs(a.getY() - b.getY()) + 1;
	}

	private static int dot(BlockPos pos, Direction dir) {
		return pos.getX() * dir.getStepX() + pos.getZ() * dir.getStepZ();
	}
}
