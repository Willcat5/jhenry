package willits.jhenry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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

	private static final VoxelShape CELL_SHAPE = Shapes.block();
	private static final VoxelShape STOP_SHAPE = Shapes.box(-0.1, -0.1, -0.1, 1.1, 1.1, 1.1);

	private PlanRenderer() {
	}

	public static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		MapConfig config = MarkManager.config();
		if (client.level == null || !config.render
				|| (MarkManager.plans().isEmpty() && MarkManager.volumePlans().isEmpty()
						&& MarkManager.volumeCorners().isEmpty())) {
			return;
		}

		PoseStack pose = context.matrices();
		MultiBufferSource consumers = context.consumers();
		if (pose == null || consumers == null) {
			return;
		}

		VertexConsumer buffer = consumers.getBuffer(RenderTypes.lines());
		Vec3 camera = client.gameRenderer.getMainCamera().position();

		for (TunnelPlan plan : MarkManager.plans()) {
			for (PlannedCell cell : plan.cells()) {
				renderBox(pose, buffer, camera, cell.pos(), colorFor(cell.segment()), config.lineWidth, CELL_SHAPE);
			}

			BlockPos stop = plan.stopPos();
			if (stop != null) {
				int color = plan.stopReason().isHazard() ? COLOR_STOP : COLOR_END;
				renderBox(pose, buffer, camera, stop, color, config.lineWidth, STOP_SHAPE);
			}

			if (plan.returnStopPos() != null) {
				renderBox(pose, buffer, camera, plan.returnStopPos(), COLOR_STOP, config.lineWidth, STOP_SHAPE);
			}
		}

		for (VolumePlan plan : MarkManager.volumePlans()) {
			for (Partition partition : plan.partitions()) {
				renderSpan(pose, buffer, camera, partition, config.lineWidth);
				renderBox(pose, buffer, camera, partition.start(), COLOR_VOLUME_START, config.lineWidth, STOP_SHAPE);
			}
		}

		for (BlockPos corner : MarkManager.volumeCorners()) {
			renderBox(pose, buffer, camera, corner, COLOR_VOLUME_CORNER, config.lineWidth, CELL_SHAPE);
		}

		renderVolumePreview(pose, buffer, camera, client, config);

		VertexConsumer oreBuffer = consumers.getBuffer(SeeThrough.lines());
		for (BlockPos ore : MarkManager.exposedOres()) {
			if (OreFilter.isOre(client.level.getBlockState(ore))) {
				renderBox(pose, oreBuffer, camera, ore, COLOR_ORE, config.lineWidth, CELL_SHAPE);
			}
		}
	}

	private static void renderVolumePreview(PoseStack pose, VertexConsumer buffer, Vec3 camera, Minecraft client,
			MapConfig config) {
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
		renderBoxSpan(pose, buffer, camera, minX, minY, minZ, maxX, maxY, maxZ, COLOR_VOLUME_PREVIEW, config.lineWidth);
	}

	private static void renderSpan(PoseStack pose, VertexConsumer buffer, Vec3 camera, Partition partition,
			float lineWidth) {
		BlockPos a = partition.min();
		BlockPos b = partition.max();
		int minX = Math.min(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxX = Math.max(a.getX(), b.getX());
		int maxY = Math.max(a.getY(), b.getY());
		int maxZ = Math.max(a.getZ(), b.getZ());
		int color = partition.pattern() == PartitionPattern.FLOOR ? COLOR_FLOOR : COLOR_UPPER;
		renderBoxSpan(pose, buffer, camera, minX, minY, minZ, maxX, maxY, maxZ, color, lineWidth);
	}

	private static void renderBoxSpan(PoseStack pose, VertexConsumer buffer, Vec3 camera,
			int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, float lineWidth) {
		float dx = maxX - minX + 1.0F;
		float dy = maxY - minY + 1.0F;
		float dz = maxZ - minZ + 1.0F;
		VoxelShape shape = Shapes.box(0.0D, 0.0D, 0.0D, dx, dy, dz);
		ShapeRenderer.renderShape(pose, buffer, shape,
				minX - camera.x, minY - camera.y, minZ - camera.z, color, lineWidth);
	}

	private static void renderBox(PoseStack pose, VertexConsumer buffer, Vec3 camera,
			BlockPos pos, int color, float lineWidth, VoxelShape shape) {
		ShapeRenderer.renderShape(pose, buffer, shape,
				pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, color, lineWidth);
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
