package willits.jhenry.controller;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
		} catch (Exception ignored) {
		}
		SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
	}
}
