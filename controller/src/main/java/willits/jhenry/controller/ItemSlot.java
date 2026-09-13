package willits.jhenry.controller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class ItemSlot extends JComponent {

	private static final Color EMPTY = new Color(0x242424);
	private static final Color FILLED = new Color(0x3A3A3A);
	private static final Color BORDER = new Color(0x151515);

	private BufferedImage icon;
	private int count;
	private int damage;
	private int maxDamage;

	public ItemSlot(int size) {
		Dimension dimension = new Dimension(size, size);
		setPreferredSize(dimension);
		setMinimumSize(dimension);
		setMaximumSize(dimension);
		setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
	}

	public void setCount(int count, int damage, int maxDamage) {
		this.count = count;
		this.damage = damage;
		this.maxDamage = maxDamage;
		repaint();
	}

	public void setIcon(BufferedImage icon) {
		this.icon = icon;
		repaint();
	}

	public void clear() {
		icon = null;
		count = 0;
		damage = 0;
		maxDamage = 0;
		setToolTipText(null);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();

		g.setColor(count > 0 ? FILLED : EMPTY);
		g.fillRect(0, 0, w, h);
		g.setColor(BORDER);
		g.drawRect(0, 0, w - 1, h - 1);

		if (icon != null) {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(icon, 1, 1, w - 2, h - 2, null);
		}

		if (count > 1) {
			String text = String.valueOf(count);
			FontMetrics metrics = g.getFontMetrics();
			int x = w - metrics.stringWidth(text) - 1;
			int y = h - 1;
			g.setColor(Color.BLACK);
			g.drawString(text, x + 1, y + 1);
			g.setColor(Color.WHITE);
			g.drawString(text, x, y);
		}

		if (maxDamage > 0) {
			int remaining = maxDamage - damage;
			float ratio = Math.max(0.0F, Math.min(1.0F, (float) remaining / maxDamage));
			int barX = 1;
			int barY = h - 3;
			int barWidth = w - 2;
			g.setColor(Color.BLACK);
			g.fillRect(barX, barY, barWidth, 2);
			g.setColor(durabilityColor(ratio));
			g.fillRect(barX, barY, Math.round(barWidth * ratio), 2);
		}

		g.dispose();
	}

	private Color durabilityColor(float ratio) {
		if (ratio > 0.5F) {
			return new Color(0x4CAF50);
		}
		if (ratio > 0.25F) {
			return new Color(0xFFEB3B);
		}
		return new Color(0xE53935);
	}
}
