package willits.jhenry.client.dig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class MoveController {

	private static final int STUCK_LIMIT = 200;
	private static final double MOVE_EPSILON = 1.0E-4D;
	private static final double CENTER_EPSILON = 0.25D;
	private static final double NUDGE_DISTANCE = 0.2D;
	private static final double NUDGE_DISTANCE_SQ = NUDGE_DISTANCE * NUDGE_DISTANCE;
	private static final int NUDGE_TICK_LIMIT = 40;

	private static BlockPos target;
	private static boolean moving;
	private static Vec3 lastPos;
	private static int stuckTicks;
	private static boolean nudging;
	private static double nudgeStartX;
	private static double nudgeStartZ;
	private static int nudgeTicks;

	private MoveController() {
	}

	public static void nudgeBackward() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		nudging = true;
		nudgeTicks = 0;
		Vec3 pos = client.player.position();
		nudgeStartX = pos.x;
		nudgeStartZ = pos.z;
	}

	public static boolean isNudging() {
		return nudging;
	}

	private static void tickNudge() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			stopNudge();
			return;
		}
		if (++nudgeTicks > NUDGE_TICK_LIMIT) {
			stopNudge();
			return;
		}
		Vec3 pos = player.position();
		double dx = pos.x - nudgeStartX;
		double dz = pos.z - nudgeStartZ;
		if (dx * dx + dz * dz >= NUDGE_DISTANCE_SQ) {
			stopNudge();
			return;
		}
		client.options.keyDown.setDown(true);
	}

	private static void stopNudge() {
		nudging = false;
		nudgeTicks = 0;
		Minecraft.getInstance().options.keyDown.setDown(false);
	}

	public static void moveTo(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		target = pos.immutable();
		moving = true;
		stuckTicks = 0;
		lastPos = client.player.position();
	}

	public static void stop() {
		releaseForward();
		stopNudge();
		moving = false;
		target = null;
		lastPos = null;
		stuckTicks = 0;
	}

	public static void tick() {
		if (nudging) {
			tickNudge();
			return;
		}

		if (!moving || target == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			stop();
			return;
		}

		if (atTarget(player, target)) {
			stop();
			return;
		}

		Vec3 pos = player.position();
		if (lastPos != null) {
			double moved = (pos.x - lastPos.x) * (pos.x - lastPos.x) + (pos.z - lastPos.z) * (pos.z - lastPos.z);
			if (moved < MOVE_EPSILON) {
				stuckTicks++;
			} else {
				stuckTicks = 0;
			}
		}
		lastPos = pos;

		if (stuckTicks > STUCK_LIMIT) {
			stop();
			return;
		}

		client.options.keyUp.setDown(true);
	}

	private static void releaseForward() {
		Minecraft.getInstance().options.keyUp.setDown(false);
	}

	public static boolean atTarget(LocalPlayer player, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		double dx = player.getX() - center.x;
		double dz = player.getZ() - center.z;
		return dx * dx + dz * dz < CENTER_EPSILON * CENTER_EPSILON
				&& Math.abs(player.getY() - pos.getY()) < 0.5D;
	}

	public static boolean isMoving() {
		return moving;
	}

	public static BlockPos target() {
		return target;
	}
}
