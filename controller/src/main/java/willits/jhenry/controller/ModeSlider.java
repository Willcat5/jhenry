package willits.jhenry.controller;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.Timer;

public final class ModeSlider extends JComponent {

	private static final Color TRACK = new Color(0x262626);
	private static final Color BORDER = new Color(0x3A3A3A);
	private static final Color THUMB = new Color(0x4CAF50);
	private static final Color THUMB_HOVER = new Color(0x66BB6A);
	private static final Color TEXT_ON = new Color(0xFFFFFF);
	private static final Color TEXT_OFF = new Color(0x9A9A9A);

	private static final int PAD = 2;
	private static final int CORNER = 5;
	private static final int STEPS = 8;
	private static final int STEP_MS = 12;

	private boolean volume;
	private float position;
	private boolean hover;
	private Consumer<Boolean> listener = value -> {
	};
	private final Timer animator;

	public ModeSlider() {
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Mining mode: Tunnel / Volume");
		setPreferredSize(new Dimension(120, 24));
		setMinimumSize(new Dimension(70, 22));

		animator = new Timer(STEP_MS, e -> stepAnimation());
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hover = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e) {
				select(e.getX() >= getWidth() / 2);
			}
		});
	}

	public void addListener(Consumer<Boolean> listener) {
		this.listener = listener;
	}

	public boolean isVolume() {
		return volume;
	}

	public void setVolume(boolean value) {
		if (value == volume) {
			return;
		}
		volume = value;
		startAnimation();
	}

	private void select(boolean value) {
		if (value == volume) {
			return;
		}
		volume = value;
		startAnimation();
		listener.accept(value);
	}

	private void startAnimation() {
		if (!animator.isRunning()) {
			animator.start();
		}
	}

	private void stepAnimation() {
		float target = volume ? 1.0F : 0.0F;
		float delta = 1.0F / STEPS;
		if (Math.abs(target - position) <= delta) {
			position = target;
			animator.stop();
		} else {
			position += Math.signum(target - position) * delta;
		}
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int arc = CORNER;

		g.setColor(TRACK);
		g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
		g.setColor(BORDER);
		g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

		int thumbW = w / 2;
		int thumbX = Math.round((w - thumbW) * position);
		g.setColor(hover ? THUMB_HOVER : THUMB);
		g.fillRoundRect(thumbX + PAD, PAD, thumbW - PAD * 2, h - PAD * 2 - 1, arc, arc);

		Font font = getFont() != null ? getFont() : new Font("Dialog", Font.PLAIN, 12);
		g.setFont(font.deriveFont(Font.BOLD, 11f));
		FontMetrics metrics = g.getFontMetrics();
		int baseline = (h - metrics.getHeight()) / 2 + metrics.getAscent();

		drawLabel(g, metrics, "Tunnel", 0, thumbW, baseline, !volume);
		drawLabel(g, metrics, "Volume", thumbW, w, baseline, volume);

		g.dispose();
	}

	private void drawLabel(Graphics2D g, FontMetrics metrics, String text, int x0, int x1, int baseline,
			boolean active) {
		int textWidth = metrics.stringWidth(text);
		int x = x0 + (x1 - x0 - textWidth) / 2;
		g.setColor(active ? TEXT_ON : TEXT_OFF);
		g.drawString(text, x, baseline);
	}
}
