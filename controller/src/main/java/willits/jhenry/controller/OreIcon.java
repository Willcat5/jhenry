package willits.jhenry.controller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class OreIcon extends JComponent {

	private static final int SIZE = 18;
	private static final int ICON = 16;
	private static final Color OUTLINE = new Color(0x3A3A3A);
	private static final Color OUTLINE_HOVER = Color.WHITE;

	private BufferedImage icon;
	private boolean hovered;

	public OreIcon(String id, Runnable onClick) {
		Dimension dimension = new Dimension(SIZE, SIZE);
		setPreferredSize(dimension);
		setMinimumSize(dimension);
		setMaximumSize(dimension);
		setToolTipText(id);
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hovered = false;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				onClick.run();
			}
		});
	}

	public void setIcon(BufferedImage image) {
		this.icon = image;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();
		int x = (w - ICON) / 2;
		int y = (h - ICON) / 2;

		if (icon != null) {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(icon, x, y, ICON, ICON, null);
		}

		if (hovered) {
			g.setColor(OUTLINE_HOVER);
		} else {
			g.setColor(OUTLINE);
		}
		g.drawRect(x, y, ICON - 1, ICON - 1);
		g.dispose();
	}
}
