package willits.jhenry.mapping;

public final class MiningSettings {

	private static boolean autoMineOres;

	private MiningSettings() {
	}

	public static boolean autoMineOres() {
		return autoMineOres;
	}

	public static void setAutoMineOres(boolean value) {
		autoMineOres = value;
	}
}
