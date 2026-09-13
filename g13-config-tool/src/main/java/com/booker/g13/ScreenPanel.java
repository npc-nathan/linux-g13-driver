package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The screen panel: a live preview of what the G13's screen is showing, which visuals are
 * enabled and in what order, how they cycle, and control of the daemon that draws them.
 *
 * Everything applies as it is changed - the daemon reads the same file and picks it up
 * within a second - so there is nothing to confirm.
 */
public class ScreenPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How often the preview reads what the daemon published, in milliseconds. */
    private static final int POLL_MS = 1000;

    /** How much the preview magnifies the 160x43 screen. */
    private static final int SCALE = 3;

    private final transient Visuals visuals = Visuals.load();

    private final DefaultListModel<String> availableModel = new DefaultListModel<>();
    private final DefaultListModel<String> enabledModel = new DefaultListModel<>();
    private final JList<String> availableList = new JList<>(availableModel);
    private final JList<String> enabledList = new JList<>(enabledModel);

    private final JCheckBox cycleBox = new JCheckBox("Cycle through them");
    private final JSpinner cycleSpinner = new JSpinner(new SpinnerNumberModel(10, 2, 600, 1));

    private final JLabel status = new JLabel(" ");
    private final Preview preview = new Preview();

    /** One screen window at a time. */
    private static JFrame window;

    /** Shows the screen window, or brings the open one forward. */
    public static void showWindow() {
        if (window != null && window.isDisplayable()) {
            window.toFront();
            window.requestFocus();
            return;
        }

        window = new JFrame("G13 Screen");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setContentPane(new ScreenPanel());
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
    }

    /**
     * Builds the panel.
     */
    public ScreenPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(preview, BorderLayout.NORTH);
        add(visualsPanel(), BorderLayout.CENTER);
        add(daemonPanel(), BorderLayout.SOUTH);

        reload();
        new Timer(POLL_MS, e -> refresh()).start();
    }

    // --- the preview ---

    /**
     * Draws the screen the daemon published, text included, so it matches the panel.
     */
    private class Preview extends JComponent {
        private static final long serialVersionUID = 1L;

        private transient Lcd lcd;
        private transient boolean[][] overlaps;
        private String note = "waiting for the daemon to publish a screen";

        Preview() {
            setPreferredSize(new Dimension(Lcd.WIDTH * SCALE + 2, Lcd.VISIBLE_HEIGHT * SCALE + 2));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        }

        @Override
        protected void paintComponent(final Graphics graphics) {
            super.paintComponent(graphics);
            final Graphics2D g = (Graphics2D) graphics;
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());

            if (lcd == null) {
                g.setColor(Color.GRAY);
                g.drawString(note, 8, 20);
                return;
            }

            final BufferedImage image = new BufferedImage(Lcd.WIDTH, Lcd.VISIBLE_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < Lcd.VISIBLE_HEIGHT; y++) {
                for (int x = 0; x < Lcd.WIDTH; x++) {
                    Color colour = lcd.pixel(x, y) ? Color.WHITE : Color.BLACK;
                    if (overlaps != null && overlaps[x][y]) {
                        // Text drawn over ink: on one-colour hardware that text is
                        // invisible, so the preview says so loudly.
                        colour = Color.ORANGE;
                    }
                    image.setRGB(x, y, colour.getRGB());
                }
            }

            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 1, 1, Lcd.WIDTH * SCALE, Lcd.VISIBLE_HEIGHT * SCALE, null);
        }
    }

    /** Reads what the daemon published and re-renders the preview. */
    private void refresh() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        final Path file = (runtime == null || runtime.isBlank() ? Path.of("/tmp") : Path.of(runtime))
                .resolve("g13-screen");

        final List<String> lines = new ArrayList<>();
        try {
            lines.addAll(Files.readAllLines(file));
        } catch (IOException e) {
            preview.lcd = null;
            preview.overlaps = null;
            preview.note = "no screen published - is g13-visuals running?";
            preview.repaint();
            status.setText("g13-visuals: " + daemonState());
            return;
        }

        final Lcd base = Lcd.of(lines);
        if (base == null) {
            return;
        }

        final boolean[][] overlaps = new boolean[Lcd.WIDTH][Lcd.HEIGHT];
        for (final Lcd.Placement placement : Lcd.placements(lines)) {
            // A line that runs past the bottom of the panel is clipped, whatever row its
            // pixels land on - so this is judged once for the whole block, not per pixel.
            final boolean belowThePanel = placement.y + LcdFont.HEIGHT > Lcd.VISIBLE_HEIGHT;

            for (int index = 0; index < placement.text.length(); index++) {
                final char character = placement.text.charAt(index);
                for (int column = 0; column < 5; column++) {
                    for (int row = 0; row < LcdFont.HEIGHT; row++) {
                        if (!LcdFont.pixel(character, column, row)) {
                            continue;
                        }
                        final int x = placement.x + index * LcdFont.ADVANCE + column;
                        final int y = placement.y + row;
                        if (x < 0 || x >= Lcd.WIDTH || y < 0 || y >= Lcd.HEIGHT) {
                            continue;
                        }
                        if (base.pixel(x, y) || belowThePanel) {
                            // Over ink, or off the bottom of the panel.
                            overlaps[x][y] = true;
                        }
                    }
                }
            }
            base.text(placement.x, placement.y, placement.text);
        }

        preview.lcd = base;
        preview.overlaps = overlaps;
        preview.repaint();
        status.setText(String.format("g13-visuals: %s   ·   showing '%s'   ·   %d enabled",
                daemonState(), visuals.active(), visuals.enabled().size()));
    }

    // --- the visuals editor ---

    private JPanel visualsPanel() {
        availableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        enabledList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        availableList.setVisibleRowCount(6);
        enabledList.setVisibleRowCount(6);

        final Map<String, String> titles = visuals.known();
        availableList.setCellRenderer(new NameRenderer(titles));
        enabledList.setCellRenderer(new NameRenderer(titles));

        final JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Visuals"));

        final JPanel lists = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lists.add(column("Available", availableList));
        lists.add(buttons());
        lists.add(column("Enabled, in order", enabledList));
        panel.add(lists, BorderLayout.CENTER);

        final JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        options.add(cycleBox);
        options.add(new JLabel("every"));
        options.add(cycleSpinner);
        options.add(new JLabel("seconds"));
        cycleBox.addActionListener(e -> apply());
        cycleSpinner.addChangeListener(e -> apply());
        panel.add(options, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel column(final String title, final JList<String> list) {
        final JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buttons() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        final JButton add = new JButton("Add \u2192");
        add.addActionListener(e -> {
            final String name = availableList.getSelectedValue();
            if (name != null) {
                visuals.enabled().add(name);
                if (visuals.enabled().size() == 1) {
                    visuals.setActive(name);
                }
                apply();
            }
        });

        final JButton remove = new JButton("\u2190 Remove");
        remove.addActionListener(e -> {
            final String name = enabledList.getSelectedValue();
            if (name != null) {
                visuals.enabled().remove(name);
                if (name.equals(visuals.active()) && !visuals.enabled().isEmpty()) {
                    visuals.setActive(visuals.enabled().get(0));
                }
                apply();
            }
        });

        final JButton up = new JButton("Up");
        up.addActionListener(e -> move(-1));

        final JButton down = new JButton("Down");
        down.addActionListener(e -> move(1));

        final JButton show = new JButton("Show now");
        show.addActionListener(e -> {
            final String name = enabledList.getSelectedValue();
            if (name != null) {
                visuals.setActive(name);
                apply();
            }
        });

        final JButton reload = new JButton("Reload");
        reload.addActionListener(e -> reload());

        for (final JButton button : new JButton[]{add, remove, up, down, show, reload}) {
            button.setAlignmentX(CENTER_ALIGNMENT);
            panel.add(button);
            panel.add(Box.createVerticalStrut(2));
        }
        return panel;
    }

    private void move(final int direction) {
        final int index = enabledList.getSelectedIndex();
        final int target = index + direction;
        final List<String> enabled = visuals.enabled();
        if (index < 0 || target < 0 || target >= enabled.size()) {
            return;
        }
        final String name = enabled.remove(index);
        enabled.add(target, name);
        apply();
        enabledList.setSelectedIndex(target);
    }

    // --- daemon control ---

    private JPanel daemonPanel() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

        final JButton restart = new JButton("Restart");
        restart.addActionListener(e -> {
            run("systemctl", "--user", "restart", "g13-visuals.service");
            SwingUtilities.invokeLater(this::refresh);
        });

        final JButton stop = new JButton("Stop");
        stop.addActionListener(e -> {
            run("systemctl", "--user", "stop", "g13-visuals.service");
            SwingUtilities.invokeLater(this::refresh);
        });

        final JButton start = new JButton("Start");
        start.addActionListener(e -> {
            run("systemctl", "--user", "start", "g13-visuals.service");
            SwingUtilities.invokeLater(this::refresh);
        });

        panel.add(new JLabel("Screen daemon:"));
        panel.add(start);
        panel.add(stop);
        panel.add(restart);
        panel.add(status);
        return panel;
    }

    /**
     * @return "running", "stopped", or "not installed".
     */
    private static String daemonState() {
        final String state = run("systemctl", "--user", "is-active", "g13-visuals.service");
        if ("active".equals(state)) {
            return "running";
        }
        if (state == null || state.isBlank()) {
            return "not installed";
        }
        return "stopped";
    }

    private static String run(final String... command) {
        try {
            final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return output;
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }

    // --- applying changes ---

    /** Writes the configuration, so the daemon picks it up without a restart. */
    private void apply() {
        visuals.setCycling(cycleBox.isSelected());
        visuals.setCycleSeconds(((Number) cycleSpinner.getValue()).doubleValue());
        try {
            visuals.save();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the visuals: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        SwingUtilities.invokeLater(this::refresh);
    }

    /** Re-reads the file and the applet list. */
    private void reload() {
        final Visuals fresh = Visuals.load();
        visuals.enabled().clear();
        visuals.enabled().addAll(fresh.enabled());
        visuals.setActive(fresh.active());
        cycleBox.setSelected(fresh.cycling());
        cycleSpinner.setValue((int) fresh.cycleSeconds());

        final Map<String, String> known = visuals.known();
        availableModel.clear();
        for (final String name : known.keySet()) {
            if (!visuals.enabled().contains(name)) {
                availableModel.addElement(name);
            }
        }
        enabledModel.clear();
        for (final String name : visuals.enabled()) {
            enabledModel.addElement(name);
        }
        ((NameRenderer) availableList.getCellRenderer()).setTitles(known);
        ((NameRenderer) enabledList.getCellRenderer()).setTitles(known);
    }

    /** Shows a visual's title where there is one, with its name underneath. */
    private static class NameRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private Map<String, String> titles;

        NameRenderer(final Map<String, String> titles) {
            this.titles = titles;
        }

        void setTitles(final Map<String, String> titles) {
            this.titles = titles;
        }

        @Override
        public java.awt.Component getListCellRendererComponent(final JList<?> list, final Object value,
                final int index, final boolean selected, final boolean focus) {
            final String name = String.valueOf(value);
            final String title = titles == null ? null : titles.get(name);
            final String label = title == null || title.equals(name) ? name : title + "   (" + name + ")";
            return super.getListCellRendererComponent(list, label, index, selected, focus);
        }
    }
}
