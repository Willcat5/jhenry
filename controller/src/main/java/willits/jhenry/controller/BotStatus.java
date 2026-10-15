package willits.jhenry.controller;

import java.util.List;

public record BotStatus(boolean online, long pid, String name, String uuid, double x, double y, double z,
		String dimension, float health, int food, boolean digging, String mode, String miningMode,
		String lastAction, long actionSeq,
		String lastError, int blocksMined, int blocksTotal, String target,
		int toolDurability, int freeSlots, int marks, int plans,
		boolean autoMine, boolean handleGravel, boolean autoTool, boolean peek, boolean pauseOnDamage,
		int maxBlocks, boolean maxBlocksOverride, int volumeLength, boolean volumeLengthOverride, int volumeSplitWidth,
		long finishedSeq, int finishedX, int finishedY, int finishedZ, String finishedDimension,
		List<String> ores, List<String> scaffolds,
		List<InventorySlot> inventory, VolumeStatus volume) {

	public static BotStatus offline() {
		return new BotStatus(false, -1, "", "", 0, 0, 0, "", 0, 0, false, "", "",
				"", 0, "", 0, 0, "", -1, -1, 0, 0, false, false, true, true, true, 100, false, 8, false, 6,
				0, 0, 0, 0, "",
				List.of(), List.of(), List.of(), null);
	}
}
