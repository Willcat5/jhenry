package willits.jhenry.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
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
import willits.jhenry.client.dig.VolumeDigger;
import willits.jhenry.client.gui.JHenryScreen;
import willits.jhenry.client.look.LookController;
import willits.jhenry.client.render.PlanRenderer;
import willits.jhenry.mapping.MarkManager;
import willits.jhenry.mapping.MiningMode;
import willits.jhenry.mapping.MiningSettings;
import willits.jhenry.mapping.TunnelMark;
import willits.jhenry.mapping.VolumePartitioner;

public class JHenryClient implements ClientModInitializer {

	public static KeyMapping openPanelKey;
	public static KeyMapping markTrapdoorKey;
	public static KeyMapping breakKey;
	public static KeyMapping stepKey;
	public static KeyMapping digKey;
	public static KeyMapping pauseKey;
	public static KeyMapping resumeKey;
	public static KeyMapping minimizeKey;

	private static java.util.UUID lastUuid;
	private static int saveTimer;
	private static int lastHurtTime;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(JHenry.MOD_ID, "main"));
		openPanelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.panel", GLFW.GLFW_KEY_J, category));
		markTrapdoorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.mark", GLFW.GLFW_KEY_M, category));
		breakKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.break", GLFW.GLFW_KEY_UNKNOWN, category));
		stepKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.step", GLFW.GLFW_KEY_UNKNOWN, category));
		digKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.dig", GLFW.GLFW_KEY_G, category));
		pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.pause", GLFW.GLFW_KEY_P, category));
		resumeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.resume", GLFW.GLFW_KEY_R, category));
		minimizeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jhenry.minimize", GLFW.GLFW_KEY_H, category));

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
				client.setScreenAndShow(new JHenryScreen());
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
				if (TunnelDigger.isRunning() || VolumeDigger.isRunning()) {
					TunnelDigger.stop();
					VolumeDigger.stop();
				} else {
					digPlan();
				}
			}
			while (resumeKey.consumeClick()) {
				BotActions.resume();
			}
			while (pauseKey.consumeClick()) {
				BotActions.pause();
			}
			while (minimizeKey.consumeClick()) {
				GLFW.glfwIconifyWindow(client.getWindow().handle());
			}

			boolean paused = client.gui.screen() != null
					&& (client.gui.screen().isPauseScreen() || client.gui.screen() instanceof ChatScreen);
			if (!paused) {
				LookController.tick();
				BlockBreaker.tick();
				MoveController.tick();
				TunnelDigger.tick();
				VolumeDigger.tick();
				updateVolumeHud(client);
			}
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(PlanRenderer::render);
	}

	private static void updateVolumeHud(Minecraft client) {
		if (MiningSettings.miningMode() != MiningMode.VOLUME) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		if (MarkManager.volumeCorners().size() != 1) {
			return;
		}
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos corner = MarkManager.volumeCorners().get(0);
		BlockPos looking = ((BlockHitResult) client.hitResult).getBlockPos();
		Direction away = player.getDirection();
		int width = VolumePartitioner.widthBetween(corner, looking, away);
		int height = VolumePartitioner.heightBetween(corner, looking);
		int length = MarkManager.config().volumeLength;
		player.sendOverlayMessage(Component.literal(
				"JHenry volume: " + width + "w x " + height + "h x " + length + "l"));
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

		if (MiningSettings.miningMode() == MiningMode.VOLUME) {
			if (MarkManager.volumeCorners().size() >= 2) {
				MarkManager.clearVolumeCorners();
			}
			MarkManager.addVolumeCorner(pos.immutable());
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("JHenry: volume corner "
						+ MarkManager.volumeCorners().size() + "/2 @ " + pos.toShortString()));
			}
			return;
		}

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
				client.player.sendSystemMessage(
						Component.literal("JHenry: marked trapdoor at " + pos.toShortString()));
			}
		}
	}
}
