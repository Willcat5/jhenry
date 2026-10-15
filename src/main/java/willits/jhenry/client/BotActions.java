package willits.jhenry.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import willits.jhenry.client.dig.TunnelDigger;
import willits.jhenry.client.dig.VolumeDigger;
import willits.jhenry.mapping.MapConfig;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningMode;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.Partition;
import willits.jhenry.mapping.TunnelMapper;
import willits.jhenry.mapping.TunnelMark;
import willits.jhenry.mapping.TunnelPlan;
import willits.jhenry.mapping.VolumeMapper;
import willits.jhenry.mapping.VolumeMark;
import willits.jhenry.mapping.VolumePlan;

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
		if (MiningSettings.miningMode() == MiningMode.VOLUME) {
			mapVolume();
			record("map");
			Persistence.save();
			return;
		}

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

	public static void mapVolume() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.level == null || player == null) {
			return;
		}

		java.util.List<BlockPos> corners = MarkManager.volumeCorners();
		if (corners.size() < 2) {
			player.displayClientMessage(Component.literal("JHenry: mark 2 volume corners first"), false);
			return;
		}

		MarkManager.clearVolumePlans();
		Direction away = player.getDirection();
		VolumeMark mark = new VolumeMark(corners.get(0), corners.get(1), away,
				MarkManager.config().volumeLength, client.level.dimension());
		VolumePlan plan = VolumeMapper.map(client.level, mark, MarkManager.config());
		MarkManager.addVolumePlan(plan);
		player.displayClientMessage(Component.literal(
				"JHenry: volume " + plan.detail() + " away=" + away), false);
	}

	public static void dig() {		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		if (MiningSettings.miningMode() == MiningMode.VOLUME) {
			digVolume();
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

	public static void digVolume() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (client.level == null || player == null) {
			return;
		}

		BlockPos pos = player.blockPosition();
		VolumePlan chosenPlan = null;
		List<Partition> candidates = new java.util.ArrayList<>();
		for (VolumePlan volumePlan : MarkManager.volumePlans()) {
			for (Partition part : volumePlan.partitions()) {
				if (part.start().getX() == pos.getX()
						&& part.start().getY() == pos.getY()
						&& part.start().getZ() == pos.getZ()) {
					candidates.add(part);
					chosenPlan = volumePlan;
				}
			}
		}

		if (candidates.isEmpty()) {
			player.displayClientMessage(Component.literal("JHenry: no partition starts here"), false);
			return;
		}

		int maxLayer = 0;
		for (Partition part : candidates) {
			maxLayer = Math.max(maxLayer, part.layer());
		}

		int targetLayer;
		if (player.getXRot() > -60.0F) {
			targetLayer = 0;
		} else {
			targetLayer = Math.min(maxLayer, countAir(client, pos) / 2);
		}

		Partition chosen = null;
		for (Partition part : candidates) {
			if (part.layer() == targetLayer) {
				chosen = part;
				break;
			}
		}
		if (chosen == null) {
			for (Partition part : candidates) {
				if (chosen == null || part.layer() < chosen.layer()) {
					chosen = part;
				}
			}
		}

		VolumeDigger.start(chosen, chosenPlan.mark().dimension(), volumeId(chosenPlan));
		if (VolumeDigger.isRunning()) {
			record("dig");
		}
	}

	public static String volumeId(VolumePlan plan) {
		VolumeMark mark = plan.mark();
		return mark.dimension().identifier() + "@"
				+ mark.a().getX() + "," + mark.a().getY() + "," + mark.a().getZ();
	}

	private static int countAir(Minecraft client, BlockPos feet) {
		int n = 0;
		BlockPos pos = feet;
		while (client.level.isLoaded(pos) && client.level.getBlockState(pos).isAir()) {
			n++;
			pos = pos.above();
		}
		return n;
	}

	public static void stopDig() {
		TunnelDigger.stop();
		VolumeDigger.stop();
		record("stop");
	}

	public static void pause() {
		if (VolumeDigger.isRunning()) {
			VolumeDigger.pauseForUser();
			record("pause");
		} else if (TunnelDigger.isRunning()) {
			TunnelDigger.pauseForUser();
			record("pause");
		}
	}

	public static void resume() {
		if (MiningSettings.miningMode() == MiningMode.VOLUME) {
			VolumeDigger.resume();
			if (VolumeDigger.isRunning()) {
				record("resume");
			}
			return;
		}
		TunnelDigger.resume();
		if (TunnelDigger.isRunning()) {
			record("resume");
		}
	}
}
