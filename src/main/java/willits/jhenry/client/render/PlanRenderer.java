package willits.jhenry.client.render;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import willits.jhenry.mapping.MapConfig;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningMode;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.OreFilter;
import willits.jhenry.mapping.Partition;
import willits.jhenry.mapping.PartitionPattern;
import willits.jhenry.mapping.PlannedCell;
import willits.jhenry.mapping.Segment;
import willits.jhenry.mapping.TunnelPlan;
import willits.jhenry.mapping.VolumeMark;
import willits.jhenry.mapping.VolumePartitioner;
import willits.jhenry.mapping.VolumePlan;

public final class PlanRenderer {

	private static final int COLOR_OUTWARD = 0xFF00FF00;
	private static final int COLOR_SIDESTEP = 0xFFFFFF00;
	private static final int COLOR_RETURN = 0xFF00AAFF;
	private static final int COLOR_RECONNECT = 0xFFFF00FF;
	private static final int COLOR_STOP = 0xFFFF0000;
	private static final int COLOR_END = 0xFFFF8800;
	private static final int COLOR_ORE = 0xFFFFD700;

	private static final int COLOR_FLOOR = 0xFF33CC33;
	private static final int COLOR_UPPER = 0xFFFFAA00;
	private static final int COLOR_VOLUME_START = 0xFFFFFFFF;
	private static final int COLOR_VOLUME_CORNER = 0xFF00FFFF;
	private static final int COLOR_VOLUME_PREVIEW = 0xFF55FFFF;

	private PlanRenderer() {
	}

	public static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		MapConfig config = MarkManager.config();
		if (client.level == null || !config.render
				|| (MarkManager.plans().isEmpty() && MarkManager.volumePlans().isEmpty()
						&& MarkManager.volumeCorners().isEmpty())) {
			return;
		}

		for (TunnelPlan plan : MarkManager.plans()) {
			for (PlannedCell cell : plan.cells()) {
				box(cell.pos(), colorFor(cell.segment()), config.lineWidth);
			}
			BlockPos stop = plan.stopPos();
			if (stop != null) {
				int color = plan.stopReason().isHazard() ? COLOR_STOP : COLOR_END;
				box(stop, color, config.lineWidth);
			}
			if (plan.returnStopPos() != null) {
				box(plan.returnStopPos(), COLOR_STOP, config.lineWidth);
			}
		}

		for (VolumePlan plan : MarkManager.volumePlans()) {
			for (Partition partition : plan.partitions()) {
				span(partition.min(), partition.max(),
						partition.pattern() == PartitionPattern.FLOOR ? COLOR_FLOOR : COLOR_UPPER, config.lineWidth);
				box(partition.start(), COLOR_VOLUME_START, config.lineWidth);
			}
		}

		for (BlockPos corner : MarkManager.volumeCorners()) {
			box(corner, COLOR_VOLUME_CORNER, config.lineWidth);
		}

		for (BlockPos ore : MarkManager.exposedOres()) {
			if (OreFilter.isOre(client.level.getBlockState(ore))) {
				box(ore, COLOR_ORE, config.lineWidth);
			}
		}

		renderVolumePreview(client, config);
	}

	private static void renderVolumePreview(Minecraft client, MapConfig config) {
		if (MiningSettings.miningMode() != MiningMode.VOLUME || MarkManager.volumeCorners().size() != 1) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.hitResult == null
				|| client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos corner = MarkManager.volumeCorners().get(0);
		BlockPos looking = ((BlockHitResult) client.hitResult).getBlockPos();
		VolumeMark preview = new VolumeMark(corner, looking, player.getDirection(), config.volumeLength,
				client.level.dimension());
		List<Partition> partitions = VolumePartitioner.partition(preview, config.volumeSplitWidth);
		if (partitions.isEmpty()) {
			return;
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Partition partition : partitions) {
			BlockPos a = partition.min();
			BlockPos b = partition.max();
			minX = Math.min(minX, Math.min(a.getX(), b.getX()));
			minY = Math.min(minY, Math.min(a.getY(), b.getY()));
			minZ = Math.min(minZ, Math.min(a.getZ(), b.getZ()));
			maxX = Math.max(maxX, Math.max(a.getX(), b.getX()));
			maxY = Math.max(maxY, Math.max(a.getY(), b.getY()));
			maxZ = Math.max(maxZ, Math.max(a.getZ(), b.getZ()));
		}
		boxSpan(minX, minY, minZ, maxX, maxY, maxZ, COLOR_VOLUME_PREVIEW, config.lineWidth);
	}

	private static void box(BlockPos pos, int color, float lineWidth) {
		Gizmos.cuboid(pos, GizmoStyle.stroke(color, lineWidth));
	}

	private static void span(BlockPos a, BlockPos b, int color, float lineWidth) {
		int minX = Math.min(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxX = Math.max(a.getX(), b.getX());
		int maxY = Math.max(a.getY(), b.getY());
		int maxZ = Math.max(a.getZ(), b.getZ());
		boxSpan(minX, minY, minZ, maxX, maxY, maxZ, color, lineWidth);
	}

	private static void boxSpan(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color,
			float lineWidth) {
		Gizmos.cuboid(new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D),
				GizmoStyle.stroke(color, lineWidth));
	}

	private static int colorFor(Segment segment) {
		return switch (segment) {
			case OUTWARD -> COLOR_OUTWARD;
			case SIDESTEP -> COLOR_SIDESTEP;
			case RETURN -> COLOR_RETURN;
			case RECONNECT -> COLOR_RECONNECT;
		};
	}
}
