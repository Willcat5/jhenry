package willits.jhenry.controller;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class IconListPanel extends JPanel {

	private static final int COLUMNS = 8;
	private static final int SCROLL_HEIGHT = 90;
	private static final int ROW_HEIGHT = 18;

	private final Supplier<List<String>> enabled;
	private final Consumer<String> add;
	private final Consumer<String> remove;
	private final Runnable onChange;
	private final Color defaultForeground;
	private JPanel grid;

	public IconListPanel(Supplier<List<String>> enabled, Consumer<String> add, Consumer<String> remove,
			Runnable onChange) {
		this.enabled = enabled;
		this.add = add;
		this.remove = remove;
		this.onChange = onChange;
		this.defaultForeground = new JTextField().getForeground();

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		add(inputRow());
		add(Box.createVerticalStrut(4));
		add(grid());
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private JPanel inputRow() {
		JTextField field = new JTextField();
		installAutocomplete(field);
		JButton addButton = new JButton("+");
		addButton.setFocusPainted(false);
		addButton.setMargin(new java.awt.Insets(0, 6, 0, 6));
		addButton.addActionListener(e -> {
			if (field.getText().isBlank()) {
				return;
			}
			List<String> ids = new ArrayList<>();
			boolean valid = true;
			for (String part : field.getText().split(",")) {
				String id = ScaffoldConfig.normalize(part);
				if (id.isEmpty()) {
					continue;
				}
				if (!BlockIds.isValid(id)) {
					valid = false;
					break;
				}
				ids.add(id);
			}
			if (!valid || ids.isEmpty()) {
				field.setForeground(Color.RED);
				return;
			}
			field.setForeground(defaultForeground);
			SoundPlayer.play("click");
			for (String id : ids) {
				add.accept(id);
			}
			field.setText("");
			refreshGrid();
			onChange.run();
		});
		field.addActionListener(e -> addButton.doClick());

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.add(field, BorderLayout.CENTER);
		row.add(addButton, BorderLayout.EAST);
		return row;
	}

	private JScrollPane grid() {
		grid = new JPanel();
		grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
		grid.setOpaque(false);
		refreshGrid();

		JScrollPane scroll = new JScrollPane(grid);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.setPreferredSize(new Dimension(160, SCROLL_HEIGHT));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, SCROLL_HEIGHT));
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private void refreshGrid() {
		if (grid == null) {
			return;
		}
		grid.removeAll();
		List<String> ids = enabled.get();
		JPanel row = null;
		for (int i = 0; i < ids.size(); i++) {
			if (i % COLUMNS == 0) {
				row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
				row.setOpaque(false);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				grid.add(row);
			}
			row.add(icon(ids.get(i)));
		}
		grid.revalidate();
		grid.repaint();
	}

	private OreIcon icon(String id) {
		OreIcon icon = new OreIcon(id, () -> {
			SoundPlayer.play("click");
			remove.accept(id);
			refreshGrid();
			onChange.run();
		});
		ItemTextures.get(id, icon::setIcon);
		return icon;
	}

	private void installAutocomplete(JTextField field) {
		JList<String> options = new JList<>();
		options.setFocusable(false);
		options.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		options.setFont(field.getFont());

		JScrollPane scroll = new JScrollPane(options);
		scroll.setBorder(null);

		JPopupMenu popup = new JPopupMenu();
		popup.setFocusable(false);
		popup.setBorder(BorderFactory.createLineBorder(new Color(0x606060)));
		popup.add(scroll);

		boolean[] updating = {false};
		String[] committed = {""};

		options.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				List<String> selected = options.getSelectedValuesList();
				if (selected.isEmpty()) {
					return;
				}
				updating[0] = true;
				field.setText(committed[0] + String.join(", ", selected));
				field.setCaretPosition(field.getText().length());
				updating[0] = false;
			}
		});

		field.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
			}

			private void update() {
				if (updating[0]) {
					return;
				}
				String text = field.getText();
				int comma = text.lastIndexOf(',');
				committed[0] = comma >= 0 ? text.substring(0, comma + 1) + " " : "";
				String segment = (comma >= 0 ? text.substring(comma + 1) : text).trim();

				List<String> matches = BlockIds.matches(segment, 100);
				if (matches.isEmpty() || segment.isBlank()) {
					popup.setVisible(false);
					return;
				}
				options.setListData(matches.toArray(new String[0]));

				FontMetrics metrics = options.getFontMetrics(options.getFont());
				int width = Math.max(field.getWidth(), 60);
				for (String match : matches) {
					width = Math.max(width, metrics.stringWidth(match) + 24);
				}
				int rows = Math.min(matches.size(), 8);
				scroll.setPreferredSize(new Dimension(width, ROW_HEIGHT * rows + 4));
				if (popup.isVisible()) {
					popup.setPopupSize(scroll.getPreferredSize());
				} else {
					popup.show(field, 0, field.getHeight());
				}
			}
		});

		field.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				popup.setVisible(false);
			}
		});

		field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "ore-down");
		field.getActionMap().put("ore-down", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!popup.isVisible() || options.getModel().getSize() == 0) {
					return;
				}
				int next = Math.min(options.getSelectedIndex() + 1, options.getModel().getSize() - 1);
				options.setSelectedIndex(next);
				options.ensureIndexIsVisible(next);
			}
		});

		field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "ore-up");
		field.getActionMap().put("ore-up", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!popup.isVisible() || options.getModel().getSize() == 0) {
					return;
				}
				int previous = Math.max(options.getSelectedIndex() - 1, 0);
				options.setSelectedIndex(previous);
				options.ensureIndexIsVisible(previous);
			}
		});

		field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "ore-tab");
		field.getActionMap().put("ore-tab", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!popup.isVisible()) {
					field.transferFocus();
					return;
				}
				List<String> selected = options.getSelectedValuesList();
				if (!selected.isEmpty()) {
					updating[0] = true;
					field.setText(committed[0] + String.join(", ", selected));
					field.setCaretPosition(field.getText().length());
					updating[0] = false;
				}
				popup.setVisible(false);
			}
		});

		field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ore-escape");
		field.getActionMap().put("ore-escape", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				popup.setVisible(false);
			}
		});
	}
}
