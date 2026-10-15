package willits.jhenry.mapping;

public final class MapConfig {
	public int maxBlocks = 100;
	public boolean maxBlocksOverride = false;
	public int volumeLength = 8;
	public boolean volumeLengthOverride = false;
	public int volumeSplitWidth = 6;
	public Side side = Side.LEFT;
	public boolean render = true;
	public boolean handleGravel = false;
	public float lineWidth = 2.0F;

	public enum Side {
		LEFT,
		RIGHT
	}
}
