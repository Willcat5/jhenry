package willits.jhenry.mapping;

public final class MiningSettings {

	private static boolean autoMineOres;
	private static boolean autoTool = true;
	private static boolean peek = true;
	private static boolean pauseOnDamage = true;

	private MiningSettings() {
	}

	public static boolean autoMineOres() {
		return autoMineOres;
	}

	public static void setAutoMineOres(boolean value) {
		autoMineOres = value;
	}

	public static boolean autoTool() {
		return autoTool;
	}

	public static void setAutoTool(boolean value) {
		autoTool = value;
	}

	public static boolean peek() {
		return peek;
	}

	public static void setPeek(boolean value) {
		peek = value;
	}

	public static boolean pauseOnDamage() {
		return pauseOnDamage;
	}

	public static void setPauseOnDamage(boolean value) {
		pauseOnDamage = value;
	}
}
