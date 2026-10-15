package willits.jhenry.controller;

public record PartitionStatus(int index, int layer, String pattern, String half, String away,
		int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
		int startX, int startY, int startZ, boolean done) {
}
