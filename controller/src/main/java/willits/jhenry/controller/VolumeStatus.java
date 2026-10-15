package willits.jhenry.controller;

import java.util.List;

public record VolumeStatus(String id, String dimension, String away, int length, int splitWidth,
		int aX, int aY, int aZ, int bX, int bY, int bZ,
		int width, int height, List<PartitionStatus> partitions) {
}
