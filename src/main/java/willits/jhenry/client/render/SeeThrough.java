package willits.jhenry.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import willits.jhenry.mixin.RenderPipelinesAccessor;
import willits.jhenry.mixin.RenderTypeInvoker;

public final class SeeThrough {

	private static RenderType lines;

	private SeeThrough() {
	}

	public static RenderType lines() {
		if (lines == null) {
			RenderPipeline pipeline = RenderPipelinesAccessor.jhenry$register(
					RenderPipeline.builder(RenderPipelinesAccessor.jhenry$linesSnippet())
							.withLocation("pipeline/jhenry_see_through")
							.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
							.withDepthWrite(false)
							.build());
			RenderSetup setup = RenderSetup.builder(pipeline)
					.bufferSize(RenderType.SMALL_BUFFER_SIZE)
					.createRenderSetup();
			lines = RenderTypeInvoker.jhenry$create("jhenry_see_through", setup);
		}
		return lines;
	}
}
