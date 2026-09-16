package willits.jhenry.controller;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class MainWindow extends JFrame {

	private static final int CARD_WIDTH = 420;
	private static final int ORE_COLUMNS = 8;

	private final List<BotConfig> bots = new ArrayList<>(BotRegistry.load());
	private final List<BotStatus> statuses = new ArrayList<>();
	private final List<BotCard> cards = new ArrayList<>();
	private final Map<BotConfig, BotStatus> lastStatus = new HashMap<>();
	private final Map<BotConfig, Long> lastSeq = new HashMap<>();
	private final Map<BotConfig, Long> lastSettingsPush = new HashMap<>();

	private final JPanel cardGrid = new JPanel();
	private JPanel oreGridPanel;
	private final JLabel statusBar = new JLabel(" ready");
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	public MainWindow() {
		super("JHenry Controller");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

		add(sidebar(), BorderLayout.WEST);

		cardGrid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JScrollPane scroll = new JScrollPane(cardGrid);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		statusBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		add(statusBar, BorderLayout.SOUTH);

		for (BotConfig bot : bots) {
			statuses.add(BotStatus.offline());
		}
		rebuildCards();

		BlockIds.fetchAsync(bots);
		SoundPlayer.setVolume(GlobalConfig.soundVolume());

		scheduler.scheduleAtFixedRate(this::poll, 0, 250, TimeUnit.MILLISECONDS);
		scheduler.scheduleAtFixedRate(this::pollMaps, 0, 1, TimeUnit.SECONDS);

		setSize(1040, 800);
		setLocationRelativeTo(null);
	}

	private JPanel sidebar() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		panel.setPreferredSize(new Dimension(184, 0));

		JLabel title = new JLabel("JHenry");
		title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 18f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(title);
		panel.add(Box.createVerticalStrut(12));

		panel.add(side("Add Bot", this::addBot));
		panel.add(side("Scan", this::scan));
		panel.add(Box.createVerticalStrut(12));
		panel.add(side("Start All", () -> commandAll("dig")));
		panel.add(side("Stop All", () -> commandAll("stop")));
		panel.add(side("Map All", () -> commandAll("map")));
		panel.add(Box.createVerticalStrut(12));
		panel.add(side("Refresh", this::poll));
		panel.add(Box.createVerticalStrut(12));

		JLabel volumeLabel = new JLabel("Volume: " + GlobalConfig.soundVolume());
		volumeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(volumeLabel);
		JSlider volume = new JSlider(0, 100, GlobalConfig.soundVolume());
		volume.setFocusable(false);
		volume.setAlignmentX(Component.LEFT_ALIGNMENT);
		volume.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		volume.addChangeListener(e -> {
			int value = volume.getValue();
			SoundPlayer.setVolume(value);
			volumeLabel.setText("Volume: " + value);
		});
		volume.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				GlobalConfig.setSoundVolume(volume.getValue());
			}
		});
		panel.add(volume);
		panel.add(Box.createVerticalStrut(12));

		JLabel oresLabel = new JLabel("Ores");
		oresLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(oresLabel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(new IconListPanel(OreConfig::enabled, OreConfig::add, OreConfig::remove, this::pushOres));
		panel.add(Box.createVerticalStrut(10));

		JLabel placementLabel = new JLabel("Placement");
		placementLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(placementLabel);
		panel.add(Box.createVerticalStrut(4));
		panel.add(new IconListPanel(ScaffoldConfig::enabled, ScaffoldConfig::add, ScaffoldConfig::remove,
				this::pushSettings));
		panel.add(Box.createVerticalStrut(4));

		JButton autoMine = new JButton();
		autoMine.setFocusPainted(false);
		autoMine.setAlignmentX(Component.LEFT_ALIGNMENT);
		autoMine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		autoMine.setText("Auto-mine: " + (ScaffoldConfig.autoMine() ? "ON" : "OFF"));
		autoMine.addActionListener(e -> {
			SoundPlayer.play("click");
			ScaffoldConfig.setAutoMine(!ScaffoldConfig.autoMine());
			autoMine.setText("Auto-mine: " + (ScaffoldConfig.autoMine() ? "ON" : "OFF"));
			pushSettings();
		});
		panel.add(autoMine);

		JButton handleGravel = new JButton();
		handleGravel.setFocusPainted(false);
		handleGravel.setAlignmentX(Component.LEFT_ALIGNMENT);
		handleGravel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		handleGravel.setText("Handle Gravel: " + (ScaffoldConfig.handleGravel() ? "ON" : "OFF"));
		handleGravel.addActionListener(e -> {
			SoundPlayer.play("click");
			ScaffoldConfig.setHandleGravel(!ScaffoldConfig.handleGravel());
			handleGravel.setText("Handle Gravel: " + (ScaffoldConfig.handleGravel() ? "ON" : "OFF"));
			pushSettings();
		});
		panel.add(handleGravel);
		panel.add(Box.createVerticalStrut(8));

		JLabel maxBlocksLabel = new JLabel("Max Blocks: " + GlobalConfig.maxBlocks());
		maxBlocksLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(maxBlocksLabel);
		JSlider maxBlocks = new JSlider(50, 500, GlobalConfig.maxBlocks());
		maxBlocks.setFocusable(false);
		maxBlocks.setAlignmentX(Component.LEFT_ALIGNMENT);
		maxBlocks.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		maxBlocks.addChangeListener(e -> maxBlocksLabel.setText("Max Blocks: " + maxBlocks.getValue()));
		maxBlocks.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				GlobalConfig.setMaxBlocks(maxBlocks.getValue());
				pushSettings();
			}
		});
		panel.add(maxBlocks);
		return panel;
	}

	private JButton side(String label, Runnable action) {
		JButton button = new JButton(label);
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		button.addActionListener(e -> {
			SoundPlayer.play("click");
			action.run();
		});
		return button;
	}

	private void pushOres() {
		List<String> ores = OreConfig.enabled();
		for (BotConfig bot : bots) {
			new Thread(() -> new BotClient(bot).sendFilter(ores), "jhenry-filter").start();
		}
	}

	private void pushSettings() {
		boolean autoMine = ScaffoldConfig.autoMine();
		boolean handleGravel = ScaffoldConfig.handleGravel();
		int maxBlocks = GlobalConfig.maxBlocks();
		List<String> scaffolds = ScaffoldConfig.enabled();
		for (BotConfig bot : bots) {
			new Thread(() -> new BotClient(bot).sendSettings(autoMine, handleGravel, maxBlocks, scaffolds),
					"jhenry-settings").start();
		}
	}

	private boolean settingsMismatch(BotStatus status) {
		if (status.ores() == null || status.scaffolds() == null) {
			return false;
		}
		return status.autoMine() != ScaffoldConfig.autoMine()
				|| status.handleGravel() != ScaffoldConfig.handleGravel()
				|| (!status.maxBlocksOverride() && status.maxBlocks() != GlobalConfig.maxBlocks())
				|| !new HashSet<>(status.ores()).equals(new HashSet<>(OreConfig.enabled()))
				|| !new HashSet<>(status.scaffolds()).equals(new HashSet<>(ScaffoldConfig.enabled()));
	}

	private void rebuildCards() {
		cardGrid.removeAll();
		cardGrid.setLayout(new BoxLayout(cardGrid, BoxLayout.Y_AXIS));
		cards.clear();

		JPanel row = null;
		for (int i = 0; i < bots.size(); i++) {
			int column = i % 2;
			if (column == 0) {
				row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
				row.setOpaque(false);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				cardGrid.add(row);
				cardGrid.add(Box.createVerticalStrut(10));
			} else {
				row.add(Box.createHorizontalStrut(10));
			}

			BotCard card = new BotCard(this, bots.get(i));
			Dimension preferred = card.getPreferredSize();
			card.setPreferredSize(new Dimension(CARD_WIDTH, preferred.height));
			card.setMaximumSize(new Dimension(CARD_WIDTH, preferred.height));
			cards.add(card);
			row.add(card);
		}

		if (row != null) {
			row.add(Box.createHorizontalGlue());
		}

		cardGrid.revalidate();
		cardGrid.repaint();
		updateCards();
	}

	private void poll() {
		for (int i = 0; i < bots.size(); i++) {
			BotStatus status = new BotClient(bots.get(i)).fetchStatus();
			if (status == null) {
				continue;
			}
			synchronized (statuses) {
				while (statuses.size() <= i) {
					statuses.add(BotStatus.offline());
				}
				statuses.set(i, status);
			}
		}
		SwingUtilities.invokeLater(this::updateCards);
	}

	private void pollMaps() {
		for (BotConfig bot : new ArrayList<>(bots)) {
			java.awt.image.BufferedImage image = new BotClient(bot).fetchMap();
			if (image == null) {
				continue;
			}
			SwingUtilities.invokeLater(() -> cardFor(bot).ifPresent(card -> card.setMap(image)));
		}
	}

	private void updateCards() {
		int online = 0;
		synchronized (statuses) {
			for (int i = 0; i < cards.size() && i < statuses.size(); i++) {
				BotConfig bot = bots.get(i);
				BotStatus status = statuses.get(i);
				BotCard card = cards.get(i);
				BotStatus previous = lastStatus.get(bot);
				handleTransitions(card, previous, status);
				if ((previous == null || !previous.online()) && status.online()) {
					new Thread(() -> new BotClient(bot).sendFilter(OreConfig.enabled()), "jhenry-filter").start();
					new Thread(() -> new BotClient(bot).sendSettings(ScaffoldConfig.autoMine(), ScaffoldConfig.handleGravel(), GlobalConfig.maxBlocks(),
							ScaffoldConfig.enabled()), "jhenry-settings").start();
					BlockIds.fetchAsync(bots);
				} else if (status.online() && settingsMismatch(status)) {
					long now = System.currentTimeMillis();
					if (now - lastSettingsPush.getOrDefault(bot, 0L) > 5000L) {
						lastSettingsPush.put(bot, now);
						new Thread(() -> new BotClient(bot).sendFilter(OreConfig.enabled()), "jhenry-filter").start();
						new Thread(() -> new BotClient(bot).sendSettings(ScaffoldConfig.autoMine(), ScaffoldConfig.handleGravel(), GlobalConfig.maxBlocks(),
								ScaffoldConfig.enabled()), "jhenry-settings").start();
					}
				}
				handleAction(card, bot, status);
				lastStatus.put(bot, status);
				card.update(status);
				if (status.online()) {
					online++;
				}
			}
		}
		statusBar.setText("  " + online + "/" + bots.size() + " online");
	}

	private void handleAction(BotCard card, BotConfig bot, BotStatus status) {
		Long previous = lastSeq.get(bot);
		if (previous != null && status.actionSeq() != previous) {
			switch (status.lastAction()) {
				case "dig", "resume" -> {
					SoundPlayer.play("start");
					card.logLine("dig started");
				}
				case "stop" -> card.logLine("stopped");
				case "map" -> card.logLine("mapped");
				default -> {
				}
			}
		}
		lastSeq.put(bot, status.actionSeq());
	}

	private void handleTransitions(BotCard card, BotStatus previous, BotStatus current) {
		if (previous == null) {
			return;
		}
		if (!previous.online() && current.online()) {
			SoundPlayer.play("online");
			card.logLine("online");
		}
		if (previous.online() && !current.online()) {
			SoundPlayer.play("offline");
			card.logLine("offline");
		}
		if (!"DONE".equals(previous.mode()) && "DONE".equals(current.mode())) {
			SoundPlayer.play("finish");
			card.logLine("tunnel complete");
		}
		if (!"PAUSED".equals(previous.mode()) && "PAUSED".equals(current.mode())) {
			SoundPlayer.play("alert");
			String reason = current.lastError();
			card.logLine("PAUSED: " + (reason == null || reason.isEmpty() ? "attention" : reason));
		}
		if (!"FAILED".equals(previous.mode()) && "FAILED".equals(current.mode())) {
			SoundPlayer.play("error");
			String error = current.lastError();
			card.logLine("ERROR: " + (error == null || error.isEmpty() ? "failed" : error));
		}
	}

	private Optional<BotCard> cardFor(BotConfig bot) {
		for (BotCard card : cards) {
			if (card.bot().equals(bot)) {
				return Optional.of(card);
			}
		}
		return Optional.empty();
	}

	public void command(BotConfig bot, String action) {
		SoundPlayer.play("click");
		new Thread(() -> {
			boolean ok = new BotClient(bot).sendCommand(action);
			SwingUtilities.invokeLater(() -> cardFor(bot)
					.ifPresent(card -> card.logLine((ok ? "OK   " : "FAIL ") + action)));
		}).start();
	}

	private void commandAll(String action) {
		List<BotConfig> targets = new ArrayList<>(bots);
		new Thread(() -> {
			for (BotConfig bot : targets) {
				boolean ok = new BotClient(bot).sendCommand(action);
				SwingUtilities.invokeLater(() -> cardFor(bot)
						.ifPresent(card -> card.logLine((ok ? "OK   " : "FAIL ") + action)));
			}
		}).start();
	}

	public void focus(BotConfig bot) {
		SoundPlayer.play("click");
		BotStatus status = statusFor(bot);
		if (status.online() && status.pid() > 0 && WindowFocus.focusByPid(status.pid())) {
			cardFor(bot).ifPresent(card -> card.logLine("focused (pid " + status.pid() + ")"));
			return;
		}

		String title = status.online() && status.name() != null && !status.name().isEmpty()
				? status.name()
				: bot.name();
		try {
			new ProcessBuilder("powershell", "-NoProfile", "-Command",
					"(New-Object -ComObject WScript.Shell).AppActivate('" + title + "')").start();
			cardFor(bot).ifPresent(card -> card.logLine("focused (by title)"));
		} catch (Exception e) {
			cardFor(bot).ifPresent(card -> card.logLine("focus failed: " + e.getMessage()));
		}
	}

	private BotStatus statusFor(BotConfig bot) {
		int index = bots.indexOf(bot);
		if (index < 0 || index >= statuses.size()) {
			return BotStatus.offline();
		}
		return statuses.get(index);
	}

	private void addBot() {
		JTextField name = new JTextField("bot1");
		JTextField host = new JTextField("127.0.0.1");
		JTextField port = new JTextField("8765");
		JTextField token = new JTextField("jhenry");

		JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
		panel.add(new JLabel("Name"));
		panel.add(name);
		panel.add(new JLabel("Host"));
		panel.add(host);
		panel.add(new JLabel("Port"));
		panel.add(port);
		panel.add(new JLabel("Token"));
		panel.add(token);

		if (JOptionPane.showConfirmDialog(this, panel, "Add Bot", JOptionPane.OK_CANCEL_OPTION)
				!= JOptionPane.OK_OPTION) {
			return;
		}

		try {
			BotConfig bot = new BotConfig(name.getText().trim(), host.getText().trim(),
					Integer.parseInt(port.getText().trim()), token.getText().trim());
			bots.add(bot);
			statuses.add(BotStatus.offline());
			lastStatus.remove(bot);
			lastSeq.remove(bot);
			BotRegistry.save(bots);
			rebuildCards();
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid port", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void scan() {
		new Thread(() -> {
			List<BotConfig> found = new ArrayList<>();
			for (int offset = 0; offset < 50; offset++) {
				int port = 8765 + offset;
				BotConfig probe = new BotConfig("", "127.0.0.1", port, "jhenry");
				BotStatus status = new BotClient(probe).fetchStatus();
				if (status != null && status.online()) {
					found.add(new BotConfig(status.name(), "127.0.0.1", port, "jhenry"));
				}
			}
			SwingUtilities.invokeLater(() -> {
				int added = 0;
				for (BotConfig bot : found) {
					boolean exists = bots.stream()
							.anyMatch(b -> b.host().equals(bot.host()) && b.port() == bot.port());
					if (!exists) {
						bots.add(bot);
						statuses.add(BotStatus.offline());
						lastStatus.remove(bot);
						lastSeq.remove(bot);
						added++;
					}
				}
				BotRegistry.save(bots);
				rebuildCards();
				statusBar.setText("  scan: " + found.size() + " found, " + added + " added");
			});
		}, "jhenry-scan").start();
	}

	public void removeBot(BotConfig bot) {
		int index = bots.indexOf(bot);
		if (index < 0) {
			return;
		}
		SoundPlayer.play("offline");
		bots.remove(index);
		if (index < statuses.size()) {
			statuses.remove(index);
		}
		lastStatus.remove(bot);
		lastSeq.remove(bot);
		BotRegistry.save(bots);
		rebuildCards();
	}
}
