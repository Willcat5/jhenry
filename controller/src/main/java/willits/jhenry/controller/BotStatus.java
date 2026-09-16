package willits.jhenry.controller;

import java.util.List;

public record BotStatus(boolean online, long pid, String name, String uuid, double x, double y, double z,
		String dimension, float health, int food, boolean digging, String mode,
		String lastAction, long actionSeq,
		String lastError, int blocksMined, int blocksTotal, String target,
		int toolDurability, int freeSlots, int marks, int plans,
		boolean autoMine, boolean handleGravel, int maxBlocks, boolean maxBlocksOverride,
		List<String> ores, List<String> scaffolds,
		List<InventorySlot> inventory) {

	public static BotStatus offline() {
		return new BotStatus(false, -1, "", "", 0, 0, 0, "", 0, 0, false, "", "",
				0, "", 0, 0, "", -1, -1, 0, 0, false, false, 100, false, List.of(), List.of(), List.of());
	}
}
