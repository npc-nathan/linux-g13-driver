package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import javax.swing.JComboBox;
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


    private final transient Visuals visuals = Visuals.load();

    private final DefaultListModel<String> availableModel = new DefaultListModel<>();
    private final DefaultListModel<String> enabledModel = new DefaultListModel<>();
    private final JList<String> availableList = new JList<>(availableModel);
    private final JList<String> enabledList = new JList<>(enabledModel);

    private final JCheckBox cycleBox = new JCheckBox("Cycle through them");
    private final JSpinner cycleSpinner = new JSpinner(new SpinnerNumberModel(10, 2, 600, 1));

    /** Who owns the screen and the four buttons beside it, and who has them right now. */
    private final JComboBox<String> buttonMode = new JComboBox<>(new String[]{"auto", "visuals", "sdk"});
    private final JLabel owner = new JLabel(" ");

    private final JLabel status = new JLabel(" ");
    private final ScreenPreview preview = new ScreenPreview();

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
        syncButtonMode();
        new Timer(POLL_MS, e -> refresh()).start();
    }

    /**
     * Shows, and follows, the mode that decides who has the screen and the four buttons.
     *
     * The file is the shared truth: g13-buttons and this window both write it, and the
     * daemon obeys it, so a change made anywhere shows up here within a second.
     */
    private void syncButtonMode() {
        final String mode = Visuals.readButtonMode();
        if (!mode.equals(buttonMode.getSelectedItem())) {
            buttonMode.setSelectedItem(mode);
        }

        final String who = Visuals.screenOwner();
        if ("sdk".equals(who)) {
            owner.setText("now: the SDK client (the preview is the last frame the visuals drew)");
        } else if ("visuals".equals(who)) {
            owner.setText("now: the visuals");
        } else {
            owner.setText("now: unknown - is g13-visuals running?");
        }
    }

    // --- the preview ---
    // The picture itself lives in ScreenPreview, shared with the applet designer, so there is one
    // place that knows how to turn the driver's lines into a screen.

    /** Reads what the daemon published and re-renders the preview. */
    private void refresh() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        final Path file = (runtime == null || runtime.isBlank() ? Path.of("/tmp") : Path.of(runtime))
                .resolve("g13-screen");

        final List<String> lines = new ArrayList<>();
        try {
            lines.addAll(Files.readAllLines(file));
        } catch (IOException e) {
            preview.showMessage("no screen published - is g13-visuals running?");
            status.setText("g13-visuals: " + daemonState());
            return;
        }

        if (!preview.show(lines)) {
            preview.showMessage("no screen published - is g13-visuals running?");
            status.setText("g13-visuals: " + daemonState());
            return;
        }

        status.setText(String.format("g13-visuals: %s   ·   showing '%s'   ·   %d enabled",
                daemonState(), visuals.active(), visuals.enabled().size()));
        syncButtonMode();
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

        // Who has the screen and the four buttons beside it. One setting, three choices, and
        // no confirm button: it applies the moment it is picked.
        final JPanel ownership = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ownership.add(new JLabel("Screen and L1-L4:"));
        buttonMode.setToolTipText("auto: an SDK client takes over while it is there   "
                + "visuals: the applet menu always keeps them   "
                + "sdk: an SDK client always has them");
        buttonMode.addActionListener(e -> {
            final Object picked = buttonMode.getSelectedItem();
            if (picked != null) {
                Visuals.writeButtonMode(picked.toString());
            }
            syncButtonMode();
        });
        ownership.add(buttonMode);
        ownership.add(owner);

        final JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(options);
        south.add(ownership);
        panel.add(south, BorderLayout.SOUTH);

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
