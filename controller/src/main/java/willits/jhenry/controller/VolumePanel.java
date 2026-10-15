package willits.jhenry.controller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

public final class VolumePanel {

	private static JFrame frame;

	private VolumePanel() {
	}

	public static void open(MainWindow owner) {
		if (frame != null && frame.isDisplayable()) {
			frame.toFront();
			frame.requestFocus();
			return;
		}
		frame = new JFrame("JHenry Volume");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setContentPane(content(owner));
		frame.pack();
		frame.setLocationByPlatform(true);
		frame.setSize(480, 460);
		frame.setVisible(true);
	}

	private static JPanel content(MainWindow owner) {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JLabel header = new JLabel("No volume mapped");
		panel.add(header, BorderLayout.NORTH);

		DefaultListModel<PartitionStatus> listModel = new DefaultListModel<>();
		JList<PartitionStatus> list = new JList<>(listModel);
		list.setCellRenderer(new PartitionRenderer());
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(440, 340));
		panel.add(scroll, BorderLayout.CENTER);

		JButton push = new JButton("Push Volume to All Bots");
		push.setFocusPainted(false);
		push.addActionListener(e -> {
			VolumeStatus volume = (VolumeStatus) header.getClientProperty("volume");
			if (volume != null) {
				owner.pushVolume(volume);
			}
		});

		JButton refresh = new JButton("Refresh");
		refresh.setFocusPainted(false);

		JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
		controls.add(push);
		controls.add(refresh);
		panel.add(controls, BorderLayout.SOUTH);

		Runnable update = () -> {
			List<BotConfig> botList = owner.botList();
			VolumeStatus volume = null;
			BotConfig source = null;
			for (BotConfig bot : botList) {
				BotStatus status = owner.statusFor(bot);
				if (status.volume() != null) {
					volume = status.volume();
					source = bot;
					break;
				}
			}
			header.putClientProperty("volume", volume);

			listModel.clear();
			if (volume == null) {
				header.setText("No volume mapped");
				return;
			}
			header.setText(String.format("Volume %s  %dx%dx%d  (%s)  -  %s",
					volume.id(), volume.width(), volume.height(), volume.length(),
					source == null ? "?" : source.name(), shortDim(volume.dimension())));
			if (volume.partitions() != null) {
				for (PartitionStatus partition : volume.partitions()) {
					listModel.addElement(partition);
				}
			}
		};

		refresh.addActionListener(e -> update.run());
		update.run();

		Timer timer = new Timer(1000, e -> update.run());
		timer.start();
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				timer.stop();
			}
		});

		return panel;
	}

	private static String shortDim(String dimension) {
		if (dimension == null) {
			return "?";
		}
		int colon = dimension.indexOf(':');
		return colon >= 0 ? dimension.substring(colon + 1) : dimension;
	}

	private static final class PartitionRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
				boolean focus) {
			super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof PartitionStatus partition) {
				String state = partition.done() ? "DONE" : "     ";
				setText(String.format("%s  layer %d  %-5s  %-5s  start %d,%d,%d",
						state, partition.layer(), partition.half(), partition.pattern(),
						partition.startX(), partition.startY(), partition.startZ()));
			}
			return this;
		}
	}
}
