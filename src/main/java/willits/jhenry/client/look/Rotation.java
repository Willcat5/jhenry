package willits.jhenry.client.look;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Applies yaw/pitch changes through the same quantized path vanilla uses for mouse input.
 *
 * Vanilla MouseHandler computes, per raw mouse count:
 *   d0 = sensitivity * 0.6 + 0.2
 *   d1 = d0^3 * 8
 *   player.turn(rawX * d1, rawY * d1)
 * and Entity.turn multiplies each argument by 0.15 before adding it to the current angle.
 * A real rotation delta is therefore always an integer multiple of 1.2 * d0^3. Applying
 * arbitrary floats (setYRot directly) produces a rotation GCD that no legitimate input can
 * make, which is a common anticheat signal. Rounding the delta to whole "mouse counts"
 * keeps every increment on that lattice.
 */
public final class Rotation {

	private static final double DEFAULT_SENSITIVITY = 0.5D;
	private static final double SENSITIVITY_FLOOR = 0.6D;
	private static final double SENSITIVITY_OFFSET = 0.2D;
	private static final double TURN_SCALE = 8.0D;
	private static final double TURN_FACTOR = 0.15D;

	private Rotation() {
	}

	public static double sensitivity() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null) {
			Double value = client.options.sensitivity().get();
			if (value != null) {
				return Mth.clamp(value, 0.0D, 1.0D);
			}
		}
		return DEFAULT_SENSITIVITY;
	}

	/** Degrees added per single raw mouse count, matching vanilla. */
	public static double degreesPerCount() {
		double base = sensitivity() * SENSITIVITY_FLOOR + SENSITIVITY_OFFSET;
		return base * base * base * TURN_SCALE * TURN_FACTOR;
	}

	public static void setAngles(LocalPlayer player, float desiredYaw, float desiredPitch) {
		if (player == null) {
			return;
		}

		double perCount = degreesPerCount();
		if (perCount <= 0.0D) {
			return;
		}
		double scale = perCount / TURN_FACTOR;

		float deltaYaw = Mth.wrapDegrees(desiredYaw - player.getYRot());
		float deltaPitch = Mth.clamp(desiredPitch, -90.0F, 90.0F) - player.getXRot();

		double countsYaw = Math.round(deltaYaw / perCount);
		double countsPitch = Math.round(deltaPitch / perCount);
		if (countsYaw == 0.0D && countsPitch == 0.0D) {
			return;
		}

		player.turn(countsYaw * scale, countsPitch * scale);
		player.setYHeadRot(player.getYRot());
	}
}
