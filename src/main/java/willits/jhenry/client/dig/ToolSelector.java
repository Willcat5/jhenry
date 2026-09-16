package willits.jhenry.client.dig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolSelector {

	private static final int LOW_DURABILITY = 20;
	private static final float LOW_PENALTY = 1000.0F;

	private ToolSelector() {
	}

	public static void hold(LocalPlayer player, BlockState state) {
		if (player == null || state == null || state.isAir()) {
			return;
		}
		int best = bestSlot(player, state);
		if (best >= 0 && best != player.getInventory().getSelectedSlot()) {
			player.getInventory().setSelectedSlot(best);
		}
	}

	public static int bestSlot(LocalPlayer player, BlockState state) {
		Inventory inventory = player.getInventory();
		int best = -1;
		float bestScore = Float.NEGATIVE_INFINITY;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			float speed = stack.getDestroySpeed(state);
			if (speed <= 1.0F) {
				continue;
			}
			float score = speed;
			if (stack.isDamageableItem()) {
				int remaining = stack.getMaxDamage() - stack.getDamageValue();
				if (remaining <= LOW_DURABILITY) {
					score -= LOW_PENALTY;
				}
				score += Math.min(remaining, 1000) * 0.0001F;
			}
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}
		return best;
	}
}
