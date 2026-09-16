package willits.jhenry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willits.jhenry.mapping.MapConfig;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.OreFilter;
import willits.jhenry.mapping.PlannedCell;
import willits.jhenry.mapping.Segment;
import willits.jhenry.mapping.TunnelPlan;

public final class PlanRenderer {

	private static final int COLOR_OUTWARD = 0xFF00FF00;
	private static final int COLOR_SIDESTEP = 0xFFFFFF00;
	private static final int COLOR_RETURN = 0xFF00AAFF;
	private static final int COLOR_RECONNECT = 0xFFFF00FF;
	private static final int COLOR_STOP = 0xFFFF0000;
	private static final int COLOR_END = 0xFFFF8800;
	private static final int COLOR_ORE = 0xFFFFD700;

	private static final VoxelShape CELL_SHAPE = Shapes.block();
	private static final VoxelShape STOP_SHAPE = Shapes.box(-0.1, -0.1, -0.1, 1.1, 1.1, 1.1);

	private PlanRenderer() {
	}

	public static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		MapConfig config = MarkManager.config();
		if (client.level == null || !config.render || MarkManager.plans().isEmpty()) {
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

		VertexConsumer oreBuffer = consumers.getBuffer(SeeThrough.lines());
		for (BlockPos ore : MarkManager.exposedOres()) {
			if (OreFilter.isOre(client.level.getBlockState(ore))) {
				renderBox(pose, oreBuffer, camera, ore, COLOR_ORE, config.lineWidth, CELL_SHAPE);
			}
		}
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
