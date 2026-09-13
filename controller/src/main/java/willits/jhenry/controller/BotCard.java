package willits.jhenry.controller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public final class BotCard extends JPanel {

	private static final Color ONLINE = new Color(0x4CAF50);
	private static final Color OFFLINE = new Color(0xE53935);
	private static final Color HP_COLOR = new Color(0xE53935);
	private static final Color FOOD_COLOR = new Color(0xD9A441);
	private static final Color PROGRESS_COLOR = new Color(0x42A5F5);
	private static final Color BORDER = new Color(0x3A3A3A);
	private static final Color SUCCESS = new Color(0x4CAF50);
	private static final Color ERROR = new Color(0xE53935);
	private static final Color WARN = new Color(0xFFB300);
	private static final Color LOG_TEXT = new Color(0xDDDDDD);
	private static final Color LOG_ERROR = new Color(0xFF6060);
	private static final Color LOG_ALERT = new Color(0xFFD24A);

	private static final int SLOT_SIZE = 20;
	private static final int SLOT_GAP = 1;

	private final MainWindow owner;
	private final BotConfig bot;

	private final SkinPanel skin = new SkinPanel(36);
	private final MapPanel map = new MapPanel(126);
	private final JLabel statusDot = new JLabel("\u25CF");
	private final JLabel nameLabel = new JLabel();
	private final JLabel infoLabel = new JLabel();
	private final JProgressBar healthBar = bar(HP_COLOR, 20);
	private final JProgressBar foodBar = bar(FOOD_COLOR, 20);
	private final JProgressBar progressBar = bar(PROGRESS_COLOR, 1);
	private final JTextPane log = new JTextPane();
	private final ItemSlot[] slots = new ItemSlot[36];
	private final JButton focusButton = new JButton("Focus");
	private final Timer blinkTimer;
	private Border focusBorder;
	private boolean blinkOn;
	private boolean attention;
	private Color attentionColor = WARN;

	public BotCard(MainWindow owner, BotConfig bot) {
		this.owner = owner;
		this.bot = bot;

		focusButton.setFocusPainted(false);
		focusButton.addActionListener(e -> owner.focus(bot));
		focusBorder = focusButton.getBorder();
		blinkTimer = new Timer(400, e -> {
			blinkOn = !blinkOn;
			updateFocusBorder();
		});

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		add(header());
		add(Box.createVerticalStrut(2));
		infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(infoLabel);
		add(Box.createVerticalStrut(6));
		add(healthBar);
		add(foodBar);
		add(progressBar);
		add(Box.createVerticalStrut(6));
		add(buttons());
		add(Box.createVerticalStrut(6));
		add(inventoryRow());
		add(Box.createVerticalStrut(6));
		add(logPane());

		update(BotStatus.offline());
	}

	private JPanel header() {
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		left.setOpaque(false);
		nameLabel.setText(bot.name());
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
		statusDot.setForeground(OFFLINE);
		left.add(statusDot);
		left.add(nameLabel);
		left.add(new JLabel("(" + bot.host() + ":" + bot.port() + ")"));

		JButton close = new JButton("\u00D7");
		close.setFocusPainted(false);
		close.setMargin(new java.awt.Insets(0, 6, 0, 6));
		close.setToolTipText("Remove bot");
		close.addActionListener(e -> owner.removeBot(bot));

		panel.add(skin, BorderLayout.WEST);
		panel.add(left, BorderLayout.CENTER);
		panel.add(close, BorderLayout.EAST);
		return panel;
	}

	private JProgressBar bar(Color color, int max) {
		JProgressBar bar = new JProgressBar(0, max);
		bar.setForeground(color);
		bar.setStringPainted(true);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		return bar;
	}

	private JPanel buttons() {
		JPanel panel = new JPanel(new GridLayout(2, 3, 4, 4));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

		panel.add(action("Dig", "dig"));
		panel.add(action("Stop", "stop"));
		panel.add(action("Resume", "resume"));
		panel.add(action("Map", "map"));
		panel.add(focusButton);
		return panel;
	}

	private JButton action(String label, String command) {
		JButton button = new JButton(label);
		button.setFocusPainted(false);
		button.addActionListener(e -> owner.command(bot, command));
		return button;
	}

	private JPanel inventoryRow() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(map);
		row.add(Box.createHorizontalStrut(8));
		row.add(inventory());
		return row;
	}

	private JPanel inventory() {
		final int gridWidth = 9 * SLOT_SIZE + 8 * SLOT_GAP;

		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.setMaximumSize(new Dimension(gridWidth, 4 * SLOT_SIZE + 3 * SLOT_GAP + 2));

		JPanel main = new JPanel(new GridLayout(3, 9, SLOT_GAP, SLOT_GAP));
		main.setOpaque(false);
		Dimension mainSize = new Dimension(gridWidth, 3 * SLOT_SIZE + 2 * SLOT_GAP);
		main.setPreferredSize(mainSize);
		main.setMinimumSize(mainSize);
		main.setMaximumSize(mainSize);
		for (int i = 9; i < 36; i++) {
			main.add(slot(i));
		}

		JPanel hotbar = new JPanel(new GridLayout(1, 9, SLOT_GAP, SLOT_GAP));
		hotbar.setOpaque(false);
		Dimension hotbarSize = new Dimension(gridWidth, SLOT_SIZE);
		hotbar.setPreferredSize(hotbarSize);
		hotbar.setMinimumSize(hotbarSize);
		hotbar.setMaximumSize(hotbarSize);
		for (int i = 0; i < 9; i++) {
			hotbar.add(slot(i));
		}

		wrapper.add(main);
		wrapper.add(Box.createVerticalStrut(2));
		wrapper.add(hotbar);
		return wrapper;
	}

	private ItemSlot slot(int index) {
		ItemSlot slot = new ItemSlot(SLOT_SIZE);
		slots[index] = slot;
		return slot;
	}

	private JScrollPane logPane() {
		log.setEditable(false);
		log.setBackground(new Color(0x1A1A1A));
		log.setForeground(LOG_TEXT);
		log.setFont(log.getFont().deriveFont(11f));
		JScrollPane scroll = new JScrollPane(log);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setPreferredSize(new Dimension(100, 56));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		return scroll;
	}

	public void update(BotStatus status) {
		statusDot.setForeground(status.online() ? ONLINE : OFFLINE);
		applyOutline(status.mode(), status.online());

		boolean failed = status.online() && "FAILED".equals(status.mode());
		boolean paused = status.online() && "PAUSED".equals(status.mode());
		boolean newAttention = failed || paused;
		Color newColor = failed ? ERROR : WARN;
		if (newAttention != attention || (newAttention && newColor != attentionColor)) {
			attention = newAttention;
			attentionColor = newColor;
			if (attention) {
				blinkOn = true;
				blinkTimer.start();
			} else {
				blinkTimer.stop();
				blinkOn = false;
			}
			updateFocusBorder();
		}

		String displayName = status.online() && status.name() != null && !status.name().isEmpty()
				? status.name()
				: bot.name();
		nameLabel.setText(displayName);

		if (status.online() && status.uuid() != null && !status.uuid().isEmpty()) {
			SkinCache.get(status.uuid(), skin::setHead);
		} else {
			skin.setHead(SkinCache.placeholder());
		}

		if (!status.online()) {
			infoLabel.setText("offline");
			healthBar.setValue(0);
			healthBar.setString("HP -");
			foodBar.setValue(0);
			foodBar.setString("Food -");
			progressBar.setValue(0);
			progressBar.setString("Progress -");
			clearInventory();
			return;
		}

		infoLabel.setText(String.format("%s   %.1f, %.1f, %.1f", shortDim(status.dimension()),
				status.x(), status.y(), status.z()));

		healthBar.setMaximum(20);
		healthBar.setValue(Math.round(status.health()));
		healthBar.setString("HP " + String.format("%.1f", status.health()));

		foodBar.setMaximum(20);
		foodBar.setValue(status.food());
		foodBar.setString("Food " + status.food());

		progressBar.setMaximum(Math.max(1, status.blocksTotal()));
		progressBar.setValue(status.blocksMined());
		progressBar.setString(status.blocksTotal() > 0
				? "Progress " + status.blocksMined() + "/" + status.blocksTotal()
				: "Progress -");

		updateInventory(status);
	}

	private void applyOutline(String mode, boolean online) {
		Color color;
		int thickness;
		if (!online) {
			color = BORDER;
			thickness = 1;
		} else if ("RUNNING".equals(mode)) {
			color = BORDER;
			thickness = 1;
		} else if ("PAUSED".equals(mode)) {
			color = WARN;
			thickness = 2;
		} else if ("FAILED".equals(mode)) {
			color = ERROR;
			thickness = 2;
		} else {
			color = SUCCESS;
			thickness = 2;
		}
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(color, thickness),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
	}

	private void updateFocusBorder() {
		if (attention && blinkOn) {
			focusButton.setBorder(BorderFactory.createLineBorder(attentionColor, 2));
		} else {
			focusButton.setBorder(focusBorder);
		}
	}

	private void clearInventory() {
		for (ItemSlot slot : slots) {
			slot.clear();
		}
	}

	private void updateInventory(BotStatus status) {
		clearInventory();
		if (status.inventory() == null) {
			return;
		}
		for (InventorySlot item : status.inventory()) {
			if (item.slot() < 0 || item.slot() >= slots.length) {
				continue;
			}
			ItemSlot slot = slots[item.slot()];
			slot.setCount(item.count(), item.damage(), item.maxDamage());
			slot.setToolTipText(shortItem(item.item()) + " x" + item.count());
			ItemTextures.get(item.item(), image -> setIcon(slot, image));
		}
	}

	private void setIcon(ItemSlot slot, BufferedImage image) {
		slot.setIcon(image);
	}

	private String shortItem(String id) {
		String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		return name.replace('_', ' ');
	}

	private String shortDim(String dimension) {
		int colon = dimension.indexOf(':');
		return colon >= 0 ? dimension.substring(colon + 1) : dimension;
	}

	public void logLine(String message) {
		SimpleAttributeSet attributes = new SimpleAttributeSet();
		StyleConstants.setForeground(attributes, colorForLog(message));
		StyledDocument document = log.getStyledDocument();
		try {
			document.insertString(document.getLength(), message + System.lineSeparator(), attributes);
		} catch (BadLocationException ignored) {
		}
		log.setCaretPosition(document.getLength());
	}

	private Color colorForLog(String message) {
		if (message.startsWith("ERROR")) {
			return LOG_ERROR;
		}
		if (message.startsWith("PAUSED") || message.startsWith("ALERT")) {
			return LOG_ALERT;
		}
		return LOG_TEXT;
	}

	public void setMap(BufferedImage image) {
		map.setMap(image);
	}

	public BotConfig bot() {
		return bot;
	}
}
