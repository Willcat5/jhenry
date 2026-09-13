package willits.jhenry.controller;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

public final class SkinPanel extends JComponent {

	private BufferedImage image;

	public SkinPanel(int size) {
		Dimension dimension = new Dimension(size, size);
		setPreferredSize(dimension);
		setMinimumSize(dimension);
		setMaximumSize(dimension);
		image = SkinCache.placeholder();
	}

	public void setHead(BufferedImage head) {
		this.image = head;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		if (image == null) {
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
		g.dispose();
	}
}
