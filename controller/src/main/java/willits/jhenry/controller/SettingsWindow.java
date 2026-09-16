package willits.jhenry.controller;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

public final class SettingsWindow {

	private static final int WIDTH = 280;

	private static JFrame frame;

	private SettingsWindow() {
	}

	public static void open(MainWindow owner) {
		if (frame != null && frame.isDisplayable()) {
			frame.toFront();
			frame.requestFocus();
			return;
		}
		frame = new JFrame("JHenry Settings");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setContentPane(content(owner));
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setMinimumSize(new Dimension(320, frame.getHeight()));
		frame.setVisible(true);
	}

	private interface Toggle {
		void set(boolean value);
	}

	private static JPanel content(MainWindow owner) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

		panel.add(check("Auto-mine ores", ScaffoldConfig.autoMine(), value -> {
			ScaffoldConfig.setAutoMine(value);
			owner.pushSettings();
		}));
		panel.add(check("Handle gravel", ScaffoldConfig.handleGravel(), value -> {
			ScaffoldConfig.setHandleGravel(value);
			owner.pushSettings();
		}));
		panel.add(check("Autotool", GlobalConfig.autoTool(), value -> {
			GlobalConfig.setAutoTool(value);
			owner.pushSettings();
		}));
		panel.add(check("Peek mine-and-replace", GlobalConfig.peek(), value -> {
			GlobalConfig.setPeek(value);
			owner.pushSettings();
		}));
		panel.add(check("Pause on damage", GlobalConfig.pauseOnDamage(), value -> {
			GlobalConfig.setPauseOnDamage(value);
			owner.pushSettings();
		}));

		panel.add(Box.createVerticalStrut(12));

		JLabel maxLabel = new JLabel("Max Blocks: " + GlobalConfig.maxBlocks());
		maxLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(maxLabel);
		JSlider max = slider(50, 500, GlobalConfig.maxBlocks());
		max.addChangeListener(e -> maxLabel.setText("Max Blocks: " + max.getValue()));
		max.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				GlobalConfig.setMaxBlocks(max.getValue());
				maxLabel.setText("Max Blocks: " + GlobalConfig.maxBlocks());
				owner.pushSettings();
			}
		});
		panel.add(max);

		panel.add(Box.createVerticalStrut(10));

		JLabel volumeLabel = new JLabel("Volume: " + GlobalConfig.soundVolume());
		volumeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(volumeLabel);
		JSlider volume = slider(0, 100, GlobalConfig.soundVolume());
		volume.addChangeListener(e -> {
			SoundPlayer.setVolume(volume.getValue());
			volumeLabel.setText("Volume: " + volume.getValue());
		});
		volume.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				GlobalConfig.setSoundVolume(volume.getValue());
			}
		});
		panel.add(volume);

		return panel;
	}

	private static JCheckBox check(String label, boolean value, Toggle toggle) {
		JCheckBox box = new JCheckBox(label, value);
		box.setFocusPainted(false);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.addActionListener(e -> toggle.set(box.isSelected()));
		return box;
	}

	private static JSlider slider(int min, int max, int value) {
		JSlider slider = new JSlider(min, max, value);
		slider.setFocusable(false);
		slider.setAlignmentX(Component.LEFT_ALIGNMENT);
		slider.setPreferredSize(new Dimension(WIDTH, 24));
		slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return slider;
	}
}
