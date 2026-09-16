package willits.jhenry.client.look;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class LookController {

	private static final int DURATION = 10;
	private static final float JITTER = 0.4F;

	private static final float DRIFT_ALPHA = 0.12F;

	private static float startYaw;
	private static float startPitch;
	private static float baseTargetYaw;
	private static float baseTargetPitch;
	private static float targetYaw;
	private static float targetPitch;
	private static int elapsed;
	private static boolean active;

	private static float driftYaw;
	private static float driftPitch;

	private LookController() {
	}

	public static void face(Direction direction) {
		setTarget(direction.toYRot(), 0.0F);
	}

	public static void lookAt(Vec3 target) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		setTarget(yaw, pitch);
	}

	public static void lookAtBlock(BlockPos pos) {
		lookAt(aimPoint(pos));
	}

	public static Vec3 aimPoint(BlockPos pos) {
		LocalPlayer player = Minecraft.getInstance().player;
		double y = pos.getY() + 0.5D;
		if (player != null && pos.getY() + 0.5D < player.getEyeY() - 0.5D) {
			y = pos.getY() + 0.85D;
		}
		return new Vec3(pos.getX() + 0.5D, y, pos.getZ() + 0.5D);
	}

	public static void setTarget(float yaw, float pitch) {
		float wrappedYaw = Mth.wrapDegrees(yaw);
		float clampedPitch = Mth.clamp(pitch, -90.0F, 90.0F);

		if (active
				&& Math.abs(Mth.wrapDegrees(wrappedYaw - baseTargetYaw)) < 0.5F
				&& Math.abs(clampedPitch - baseTargetPitch) < 0.5F) {
			return;
		}

		float jitter = JITTER;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float landingYaw = (random.nextFloat() * 2.0F - 1.0F) * jitter;
		float landingPitch = (random.nextFloat() * 2.0F - 1.0F) * jitter;

		LocalPlayer player = Minecraft.getInstance().player;
		startYaw = player != null ? player.getYRot() : wrappedYaw;
		startPitch = player != null ? player.getXRot() : clampedPitch;
		baseTargetYaw = wrappedYaw;
		baseTargetPitch = clampedPitch;
		targetYaw = Mth.wrapDegrees(wrappedYaw + landingYaw);
		targetPitch = Mth.clamp(clampedPitch + landingPitch, -90.0F, 90.0F);
		elapsed = 0;
		active = true;
	}

	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			active = false;
			return;
		}

		if (!active) {
			if (driftYaw == 0.0F && driftPitch == 0.0F) {
				return;
			}
			driftYaw *= 0.7F;
			driftPitch *= 0.7F;
			if (Math.abs(driftYaw) < 0.02F && Math.abs(driftPitch) < 0.02F) {
				driftYaw = 0.0F;
				driftPitch = 0.0F;
				return;
			}
			apply(player, targetYaw + driftYaw, targetPitch + driftPitch);
			return;
		}

		updateDrift(JITTER);

		int duration = DURATION;
		elapsed++;
		float t = Math.min(1.0F, (float) elapsed / duration);
		float eased = t * t * (3.0F - 2.0F * t);
		float fade = 1.0F - eased;

		float deltaYaw = Mth.wrapDegrees(targetYaw - startYaw);
		float yaw = startYaw + deltaYaw * eased + driftYaw * fade;
		float pitch = startPitch + (targetPitch - startPitch) * eased + driftPitch * fade;

		if (t >= 1.0F) {
			active = false;
			driftYaw = 0.0F;
			driftPitch = 0.0F;
			apply(player, targetYaw, targetPitch);
			return;
		}
		apply(player, yaw, pitch);
	}

	private static void updateDrift(float amplitude) {
		if (amplitude <= 0.0F) {
			driftYaw = 0.0F;
			driftPitch = 0.0F;
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float noiseYaw = (random.nextFloat() * 2.0F - 1.0F) * amplitude;
		float noisePitch = (random.nextFloat() * 2.0F - 1.0F) * amplitude;
		driftYaw += (noiseYaw - driftYaw) * DRIFT_ALPHA;
		driftPitch += (noisePitch - driftPitch) * DRIFT_ALPHA;
	}

	private static void apply(LocalPlayer player, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
		player.setYHeadRot(yaw);
	}

	public static boolean isActive() {
		return active;
	}

	public static void stop() {
		active = false;
		driftYaw = 0.0F;
		driftPitch = 0.0F;
	}
}
