package willits.jhenry.mapping;

public final class MapConfig {
	public int maxBlocks = 100;
	public Side side = Side.LEFT;
	public boolean render = true;
	public float lineWidth = 2.0F;

	public enum Side {
		LEFT,
		RIGHT
	}
}
