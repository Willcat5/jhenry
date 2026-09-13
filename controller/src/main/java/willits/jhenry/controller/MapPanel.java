package willits.jhenry.controller;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class MapPanel extends JComponent {

	private static final Color BACKGROUND = new Color(0x141414);
	private static final Color BORDER = new Color(0x151515);

	private BufferedImage image;

	public MapPanel(int size) {
		Dimension dimension = new Dimension(size, size);
		setPreferredSize(dimension);
		setMinimumSize(dimension);
		setMaximumSize(dimension);
	}

	public void setMap(BufferedImage image) {
		this.image = image;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();

		g.setColor(BACKGROUND);
		g.fillRect(0, 0, w, h);

		if (image != null) {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(image, 0, 0, w, h, null);
		}

		g.setColor(BORDER);
		g.drawRect(0, 0, w - 1, h - 1);
		g.dispose();
	}
}
