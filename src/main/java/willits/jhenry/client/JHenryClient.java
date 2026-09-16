package willits.jhenry.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import willits.jhenry.JHenry;
import willits.jhenry.bot.ControlServer;
import willits.jhenry.client.dig.BlockBreaker;
import willits.jhenry.client.dig.MoveController;
import willits.jhenry.client.dig.TunnelDigger;
import willits.jhenry.client.gui.JHenryScreen;
import willits.jhenry.client.look.LookController;
import willits.jhenry.client.render.PlanRenderer;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.TunnelMark;

public class JHenryClient implements ClientModInitializer {

	public static KeyMapping openPanelKey;
	public static KeyMapping markTrapdoorKey;
	public static KeyMapping breakKey;
	public static KeyMapping stepKey;
	public static KeyMapping digKey;
	public static KeyMapping resumeKey;
	public static KeyMapping minimizeKey;

	private static java.util.UUID lastUuid;
	private static int saveTimer;
	private static int lastHurtTime;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(JHenry.MOD_ID, "main"));
		openPanelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.panel", GLFW.GLFW_KEY_J, category));
		markTrapdoorKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.mark", GLFW.GLFW_KEY_M, category));
		breakKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.break", GLFW.GLFW_KEY_B, category));
		stepKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.step", GLFW.GLFW_KEY_N, category));
		digKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.dig", GLFW.GLFW_KEY_G, category));
		resumeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.resume", GLFW.GLFW_KEY_R, category));
		minimizeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.jhenry.minimize", GLFW.GLFW_KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			client.options.pauseOnLostFocus = false;

			if (!ControlServer.isRunning() && client.player != null) {
				ControlServer.start(
						ControlServer.derivePort(client.player.getName().getString()),
						ControlServer.DEFAULT_TOKEN);
			}

			if (client.player != null) {
				java.util.UUID uuid = client.player.getUUID();
				if (!uuid.equals(lastUuid)) {
					lastUuid = uuid;
					lastHurtTime = 0;
					Persistence.load(uuid);
				}
				int hurt = client.player.hurtTime;
				if (hurt > 0 && lastHurtTime == 0) {
					TunnelDigger.notifyDamage();
				}
				lastHurtTime = hurt;
				saveTimer++;
				if (saveTimer >= 400) {
					saveTimer = 0;
					Persistence.save();
				}
			}

			while (openPanelKey.consumeClick()) {
				client.setScreen(new JHenryScreen());
			}
			while (markTrapdoorKey.consumeClick()) {
				markLookedAt(client);
			}
			while (breakKey.consumeClick()) {
				if (BlockBreaker.isBreaking()) {
					BlockBreaker.stop();
				} else {
					breakLookedAt(client);
				}
			}
			while (stepKey.consumeClick()) {
				if (MoveController.isMoving()) {
					MoveController.stop();
				} else {
					stepForward(client);
				}
			}
			while (digKey.consumeClick()) {
				if (TunnelDigger.isRunning()) {
					TunnelDigger.stop();
				} else {
					digPlan();
				}
			}
			while (resumeKey.consumeClick()) {
				TunnelDigger.resume();
			}
			while (minimizeKey.consumeClick()) {
				GLFW.glfwIconifyWindow(client.getWindow().handle());
			}

			boolean paused = client.screen != null
					&& (client.screen.isPauseScreen() || client.screen instanceof ChatScreen);
			if (!paused) {
				LookController.tick();
				BlockBreaker.tick();
				MoveController.tick();
				TunnelDigger.tick();
			}
		});

		WorldRenderEvents.AFTER_ENTITIES.register(PlanRenderer::render);
	}

	public static void digPlan() {
		BotActions.dig();
	}

	public static void stepForward(Minecraft client) {
		if (client.player == null) {
			return;
		}
		MoveController.moveTo(client.player.blockPosition().relative(client.player.getDirection()));
	}

	public static void breakLookedAt(Minecraft client) {
		if (client.level == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
		BlockBreaker.breakBlock(pos);
	}

	public static void markLookedAt(Minecraft client) {
		if (client.level == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
		BlockState state = client.level.getBlockState(pos);
		if (!(state.getBlock() instanceof TrapDoorBlock)) {
			return;
		}

		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		boolean open = state.getValue(BlockStateProperties.OPEN);
		TunnelMark mark = new TunnelMark(pos.immutable(), facing, open, client.level.dimension());

		if (!MarkManager.marks().contains(mark)) {
			MarkManager.addMark(mark);
			Persistence.save();
			if (client.player != null) {
				client.player.displayClientMessage(
						Component.literal("JHenry: marked trapdoor at " + pos.toShortString()), false);
			}
		}
	}
}
