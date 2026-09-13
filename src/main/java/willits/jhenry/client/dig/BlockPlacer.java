package willits.jhenry.client.dig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import willits.jhenry.client.look.LookController;

public final class BlockPlacer {

	public enum Status {
		WORKING,
		DONE,
		FAILED
	}

	private static final int ATTEMPT_LIMIT = 40;

	private static BlockPos target;
	private static int attempts;

	private BlockPlacer() {
	}

	public static void place(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (!client.level.getBlockState(pos).isAir()) {
			return;
		}
		target = pos.immutable();
		attempts = 0;
		LookController.lookAtBlock(target);
	}

	public static void stop() {
		target = null;
		attempts = 0;
	}

	public static Status tick() {
		if (target == null) {
			return Status.DONE;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			stop();
			return Status.FAILED;
		}

		if (!client.level.getBlockState(target).isAir()) {
			stop();
			return Status.DONE;
		}

		if (++attempts > ATTEMPT_LIMIT) {
			stop();
			return Status.FAILED;
		}

		if (LookController.isActive()) {
			return Status.WORKING;
		}

		BlockHitResult hit = client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK
				? (BlockHitResult) client.hitResult
				: null;

		boolean valid = hit != null
				&& isNeighbor(hit.getBlockPos(), target)
				&& hit.getBlockPos().relative(hit.getDirection()).equals(target);

		if (!valid) {
			LookController.lookAtBlock(target);
			return Status.WORKING;
		}

		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		return Status.WORKING;
	}

	private static boolean isNeighbor(BlockPos a, BlockPos b) {
		int dx = Math.abs(a.getX() - b.getX());
		int dy = Math.abs(a.getY() - b.getY());
		int dz = Math.abs(a.getZ() - b.getZ());
		return dx + dy + dz == 1;
	}

	public static boolean isPlacing() {
		return target != null;
	}

	public static BlockPos target() {
		return target;
	}
}
