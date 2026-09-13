package willits.jhenry.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import willits.jhenry.client.dig.TunnelDigger;
import willits.jhenry.mapping.MapConfig;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.TunnelMapper;
import willits.jhenry.mapping.TunnelMark;
import willits.jhenry.mapping.TunnelPlan;

public final class BotActions {

	private static String lastAction = "";
	private static long actionSeq;

	private BotActions() {
	}

	public static String lastAction() {
		return lastAction;
	}

	public static long actionSeq() {
		return actionSeq;
	}

	private static void record(String action) {
		lastAction = action;
		actionSeq++;
	}

	public static void map() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.level == null || player == null) {
			return;
		}

		MarkManager.clearPlans();
		MapConfig config = MarkManager.config();
		int totalBlocks = 0;
		int discarded = 0;

		for (TunnelMark mark : new ArrayList<>(MarkManager.marks())) {
			TunnelPlan plan = TunnelMapper.map(client.level, mark, config);
			if (plan.blockCount() == 0) {
				MarkManager.removeMark(mark);
				discarded++;
				player.displayClientMessage(Component.literal(
						"JHenry: discarded mark @ " + mark.entrance().toShortString()
								+ " (no mineable blocks)"), false);
				continue;
			}
			MarkManager.addPlan(plan);
			totalBlocks += plan.blockCount();
			player.displayClientMessage(Component.literal(
					"Tunnel @ " + mark.entrance().toShortString()
							+ " dir=" + plan.direction()
							+ " len=" + plan.length()
							+ " blocks=" + plan.blockCount()
							+ " stop=" + plan.stopReason().label()
							+ " [" + plan.detail() + "]"), false);
		}

		player.displayClientMessage(Component.literal(
				"JHenry: mapped " + MarkManager.plans().size() + " tunnel(s), " + totalBlocks + " blocks total"
						+ (discarded > 0 ? " (" + discarded + " discarded)" : "")), false);
		record("map");
		Persistence.save();
	}

	public static void dig() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		List<TunnelPlan> plans = MarkManager.plans();
		if (plans.isEmpty()) {
			return;
		}

		BlockPos pos = player.blockPosition();
		TunnelPlan match = null;
		for (TunnelPlan plan : plans) {
			if (plan.mark().entrance().equals(pos)) {
				match = plan;
				break;
			}
		}

		if (match == null) {
			player.displayClientMessage(Component.literal("JHenry: no plan starts here"), false);
			return;
		}
		TunnelDigger.start(match);
		if (TunnelDigger.isRunning()) {
			record("dig");
		}
	}

	public static void stopDig() {
		TunnelDigger.stop();
		record("stop");
	}

	public static void resume() {
		TunnelDigger.resume();
		if (TunnelDigger.isRunning()) {
			record("resume");
		}
	}
}
