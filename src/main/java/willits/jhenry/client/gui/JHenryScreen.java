package willits.jhenry.client.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import willits.jhenry.client.BotActions;
import willits.jhenry.client.JHenryClient;
import willits.jhenry.client.Persistence;
import willits.jhenry.client.dig.TunnelDigger;
import willits.jhenry.mapping.MapConfig;
import willits.jhenry.mapping.MarkManager;

public class JHenryScreen extends BaseOwoScreen<FlowLayout> {

	private static final int PANEL_WIDTH = 300;

	private static final int PANEL_BG = 0xE6121212;
	private static final int PANEL_BORDER = 0xFF3A3A3A;
	private static final int ROOT_DIM = 0xA0000000;
	private static final int BUTTON_BG = 0xFF262626;
	private static final int BUTTON_HOVER = 0xFF3C3C3C;
	private static final int BUTTON_DISABLED = 0xFF181818;
	private static final int BUTTON_TOP = 0xFF4A4A4A;
	private static final int BUTTON_BOTTOM = 0xFF0E0E0E;

	private static final Color TITLE = Color.ofRgb(0xFFD700);
	private static final Color TEXT = Color.ofRgb(0xFFFFFF);
	private static final Color MUTED = Color.ofRgb(0xC8C8C8);

	private static final ButtonComponent.Renderer DARK_BUTTON = (OwoUIGraphics context, ButtonComponent button, float delta) -> {
		int x = button.getX();
		int y = button.getY();
		int w = button.getWidth();
		int h = button.getHeight();
		int background = !button.active() ? BUTTON_DISABLED : button.isHovered() ? BUTTON_HOVER : BUTTON_BG;
		context.fill(x, y, x + w, y + h, background);
		context.fill(x, y, x + w, y + 1, BUTTON_TOP);
		context.fill(x, y + h - 1, x + w, y + h, BUTTON_BOTTOM);
	};

	private LabelComponent statusLabel;

	public JHenryScreen() {
		super(Component.literal("JHenry"));
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(FlowLayout root) {
		root.surface(Surface.flat(ROOT_DIM));
		root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

		MapConfig config = MarkManager.config();

		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.surface(Surface.flat(PANEL_BG).and(Surface.outline(PANEL_BORDER)));
		panel.padding(Insets.of(10));
		panel.gap(4);

		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(4);
		LabelComponent title = UIComponents.label(Component.literal("JHenry")).color(TITLE).shadow(true);
		title.horizontalSizing(Sizing.expand());
		header.child(title);
		header.child(closeButton());
		panel.child(header);

		statusLabel = UIComponents.label(Component.empty()).color(TEXT);
		panel.child(statusLabel);

		panel.child(section("Mapping"));
		panel.child(button("Map Tunnels", () -> {
			BotActions.map();
			refreshStatus();
		}));
		panel.child(button("Mark Looked-At Trapdoor", () -> {
			JHenryClient.markLookedAt(Minecraft.getInstance());
			refreshStatus();
		}));

		panel.child(section("Digging"));
		panel.child(row(
				button("Dig", () -> {
					if (TunnelDigger.isRunning()) {
						TunnelDigger.stop();
					} else {
						JHenryClient.digPlan();
					}
					this.onClose();
				}),
				button("Resume", TunnelDigger::resume)));
		panel.child(row(
				button("Clear Marks", () -> {
					MarkManager.clearMarks();
					MarkManager.clearPlans();
					Persistence.save();
					refreshStatus();
				}),
				button("Clear Plans", () -> {
					MarkManager.clearPlans();
					Persistence.save();
					refreshStatus();
				})));

		panel.child(section("Settings"));

		panel.child(UIComponents.checkbox(Component.literal("Override Max Blocks"))
				.checked(config.maxBlocksOverride)
				.onChanged(value -> {
					config.maxBlocksOverride = value;
					Persistence.save();
				}));

		var maxSlider = UIComponents.slider(Sizing.fill(100));
		maxSlider.value((config.maxBlocks - 50) / 450.0);
		maxSlider.message(value -> Component.literal("Max Blocks: " + mapMaxBlocks(Double.parseDouble(value))));
		maxSlider.onChanged().subscribe(value -> {
			if (config.maxBlocksOverride) {
				config.maxBlocks = mapMaxBlocks(value);
				Persistence.save();
			}
		});
		panel.child(maxSlider);

		panel.child(UIComponents.checkbox(Component.literal("Render Tunnel Path"))
				.checked(config.render)
				.onChanged(value -> config.render = value));

		ButtonComponent sidestep = UIComponents.button(Component.literal("Sidestep: " + config.side), b -> {
			config.side = config.side == MapConfig.Side.LEFT ? MapConfig.Side.RIGHT : MapConfig.Side.LEFT;
			b.setMessage(Component.literal("Sidestep: " + config.side));
		});
		sidestep.horizontalSizing(Sizing.fill(100));
		sidestep.renderer(DARK_BUTTON);
		panel.child(sidestep);

		root.child(panel);
		refreshStatus();
	}

	private void refreshStatus() {
		if (statusLabel != null) {
			statusLabel.text(Component.literal(
					"Marks: " + MarkManager.marks().size() + "    Plans: " + MarkManager.plans().size()));
		}
	}

	private int mapMaxBlocks(double value) {
		return 50 + (int) Math.round(value * 450.0);
	}

	private UIComponent section(String title) {
		return UIComponents.label(Component.literal(title)).color(MUTED).shadow(true);
	}

	private UIComponent row(UIComponent left, UIComponent right) {
		FlowLayout flow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		flow.gap(4);
		left.horizontalSizing(Sizing.expand(50));
		right.horizontalSizing(Sizing.expand(50));
		flow.child(left);
		flow.child(right);
		return flow;
	}

	private ButtonComponent button(String label, Runnable action) {
		ButtonComponent button = UIComponents.button(Component.literal(label), b -> action.run());
		button.horizontalSizing(Sizing.fill(100));
		button.renderer(DARK_BUTTON);
		return button;
	}

	private ButtonComponent closeButton() {
		ButtonComponent button = UIComponents.button(Component.literal("X"), b -> this.onClose());
		button.horizontalSizing(Sizing.fixed(20));
		button.renderer(DARK_BUTTON);
		return button;
	}
}
