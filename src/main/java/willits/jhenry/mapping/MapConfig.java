package willits.jhenry.mapping;

public final class MapConfig {
	public int maxBlocks = 100;
	public boolean maxBlocksOverride = false;
	public Side side = Side.LEFT;
	public boolean render = true;
	public boolean handleGravel = false;
	public float lineWidth = 2.0F;

	public enum Side {
		LEFT,
		RIGHT
	}
}
