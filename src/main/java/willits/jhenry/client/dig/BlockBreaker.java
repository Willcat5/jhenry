package willits.jhenry.client.dig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willits.jhenry.client.look.LookController;

public final class BlockBreaker {

	private static BlockPos target;
	private static Vec3 aim;
	private static boolean faceAim;
	private static boolean breaking;
	private static boolean holdingAttack;

	private BlockBreaker() {
	}

	public static void breakBlock(BlockPos pos) {
		breakBlock(pos, LookController.aimPoint(pos));
	}

	public static void breakBlock(BlockPos pos, Vec3 aimPoint) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.level.getBlockState(pos).isAir()) {
			return;
		}
		target = pos.immutable();
		aim = aimPoint;
		faceAim = false;
		breaking = true;
		LookController.lookAt(aim);
	}

	public static void stop() {
		releaseAttack();
		target = null;
		aim = null;
		faceAim = false;
		breaking = false;
	}

	public static void tick() {
		if (!breaking || target == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			stop();
			return;
		}

		BlockState state = client.level.getBlockState(target);
		if (state.isAir()) {
			stop();
			return;
		}

		if (LookController.isActive()) {
			releaseAttack();
			return;
		}

		if (!isLookingAt(client, target)) {
			releaseAttack();
			if (!faceAim) {
				Vec3 face = exposedFaceAim(client, target);
				if (face != null) {
					faceAim = true;
					aim = face;
					LookController.lookAt(face);
					return;
				}
			}
			LookController.lookAt(aim != null ? aim : LookController.aimPoint(target));
			return;
		}

		holdAttack(client);
	}

	private static Vec3 exposedFaceAim(Minecraft client, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = pos.relative(direction);
			if (client.level.isLoaded(neighbor) && client.level.getBlockState(neighbor).isAir()) {
				return new Vec3(
						pos.getX() + 0.5D + direction.getStepX() * 0.5D,
						pos.getY() + 0.5D + direction.getStepY() * 0.5D,
						pos.getZ() + 0.5D + direction.getStepZ() * 0.5D);
			}
		}
		return null;
	}

	private static boolean isLookingAt(Minecraft client, BlockPos pos) {
		HitResult hit = client.hitResult;
		return hit != null && hit.getType() == HitResult.Type.BLOCK
				&& ((BlockHitResult) hit).getBlockPos().equals(pos);
	}

	private static void holdAttack(Minecraft client) {
		client.options.keyAttack.setDown(true);
		holdingAttack = true;
	}

	public static void holdAttack() {
		holdAttack(Minecraft.getInstance());
	}

	public static void releaseAttack() {
		if (holdingAttack) {
			Minecraft.getInstance().options.keyAttack.setDown(false);
			holdingAttack = false;
		}
	}

	public static boolean isBreaking() {
		return breaking;
	}

	public static BlockPos target() {
		return target;
	}
}
