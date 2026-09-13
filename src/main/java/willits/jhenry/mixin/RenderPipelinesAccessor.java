package willits.jhenry.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

	@Accessor("LINES_SNIPPET")
	static RenderPipeline.Snippet jhenry$linesSnippet() {
		throw new AssertionError();
	}

	@Invoker("register")
	static RenderPipeline jhenry$register(RenderPipeline pipeline) {
		throw new AssertionError();
	}
}
