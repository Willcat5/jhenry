package willits.jhenry.client.dig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willits.jhenry.client.look.LookController;

public final class BlockPlacer {

	public enum Status {
		WORKING,
		DONE,
		FAILED
	}

	private static final int ATTEMPT_LIMIT = 40;

	private static BlockPos target;
	private static Vec3 aim;
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
		aim = placementAim(client, client.player, target);
		LookController.lookAt(aim);
	}

	public static void stop() {
		target = null;
		aim = null;
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
			aim = placementAim(client, player, target);
			LookController.lookAt(aim);
			return Status.WORKING;
		}

		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		return Status.WORKING;
	}

	private static Vec3 placementAim(Minecraft client, LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = target.relative(direction);
			if (!client.level.isLoaded(neighbor)) {
				continue;
			}
			BlockState state = client.level.getBlockState(neighbor);
			if (!state.isSolid() || !state.getFluidState().isEmpty()) {
				continue;
			}
			Vec3 face = closestFacePoint(neighbor, direction, eye);
			Vec3 toward = face.subtract(eye);
			if (toward.lengthSqr() < 1.0E-6D) {
				continue;
			}
			Vec3 end = face.add(toward.normalize().scale(0.01D));
			BlockHitResult hit = client.level.clip(new ClipContext(eye, end,
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.BLOCK
					|| !hit.getBlockPos().equals(neighbor)
					|| !hit.getBlockPos().relative(hit.getDirection()).equals(target)) {
				continue;
			}
			double distance = toward.lengthSqr();
			if (distance < bestDistance) {
				bestDistance = distance;
				best = face;
			}
		}
		return best != null ? best : LookController.aimPoint(target);
	}

	private static Vec3 closestFacePoint(BlockPos neighbor, Direction direction, Vec3 eye) {
		double minX = neighbor.getX();
		double minY = neighbor.getY();
		double minZ = neighbor.getZ();
		double maxX = minX + 1.0D;
		double maxY = minY + 1.0D;
		double maxZ = minZ + 1.0D;
		switch (direction) {
			case EAST -> minX = maxX = neighbor.getX();
			case WEST -> minX = maxX = neighbor.getX() + 1.0D;
			case UP -> minY = maxY = neighbor.getY();
			case DOWN -> minY = maxY = neighbor.getY() + 1.0D;
			case SOUTH -> minZ = maxZ = neighbor.getZ();
			case NORTH -> minZ = maxZ = neighbor.getZ() + 1.0D;
		}
		return new Vec3(
				clampToFace(eye.x, minX, maxX),
				clampToFace(eye.y, minY, maxY),
				clampToFace(eye.z, minZ, maxZ));
	}

	private static double clampToFace(double value, double min, double max) {
		final double inset = 0.1D;
		if (max - min <= 2 * inset) {
			return (min + max) * 0.5D;
		}
		return Math.max(min + inset, Math.min(max - inset, value));
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
