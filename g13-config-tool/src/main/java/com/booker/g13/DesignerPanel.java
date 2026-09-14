package com.booker.g13;

import javax.swing.BorderFactory;
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
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The applet designer: one applet's widgets, the fields of the selected one, and the pad's own
 * picture of it.
 *
 * Every change is written to the applet's file as it is made — there is no Apply, and nothing to
 * lose by closing the window. While it is open, the applet being edited is the one on the pad, so
 * the thing being designed is the thing being seen.
 *
 * The picture is not drawn here. It is the frame the daemon published, read back from the driver's
 * runtime directory, so this window, the Screen window and the pad cannot disagree about a design.
 * Whether a design is a *good* one is not decided here either: that is {@code g13-applet check},
 * which knows the rules the pad has (text has to land on blank pixels), and the status line says
 * how to run it. A second set of rules in this window could disagree with the checker, which would
 * be worse than having none.
 */
public class DesignerPanel {

    /** The fields that hold a number, so the window can offer a spinner for them. */
    private static final List<String> NUMERIC = List.of("x", "y", "w", "h", "max", "count", "len",
            "thick", "size", "r", "margin", "scroll_width", "scroll_speed");

    private final String appletName;
    private final JFrame frame;
    private final ScreenPreview preview = new ScreenPreview();
    private final JLabel previewNote = new JLabel(" ");
    private final DefaultListModel<String> widgetModel = new DefaultListModel<>();
    private final JList<String> widgetList = new JList<>(widgetModel);
    private final JPanel widgetFields = new JPanel(new GridBagLayout());
    private final JTextField titleField = new JTextField(16);
    private final JSpinner intervalSpinner =
            new JSpinner(new SpinnerNumberModel(1.0, 0.2, 600.0, 0.1));
    private final JCheckBox borderBox = new JCheckBox("frame and title");
    private final JSpinner followSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 3600.0, 1.0));
    private final JTextArea aliasesArea = new JTextArea(4, 24);
    private final JComboBox<String> addType =
            new JComboBox<>(AppletEditor.TYPES.toArray(new String[0]));
    private final JLabel status = new JLabel(" ");
    private AppletEditor editor;

    /** True while the window is filling its own fields in, so their listeners do not write back. */
    private boolean loading;

    /** Which widget's fields are on show, or -1. */
    private int selected = -1;

    /**
     * Opens the designer for an applet, creating the file if it is not there yet.
     * @param name The applet's name, without .json.
     */
    public static void showWindow(final String name) {
        new DesignerPanel(name);
    }

    private DesignerPanel(final String name) {
        this.appletName = name;
        try {
            this.editor = Files.exists(AppletEditor.path(name))
                    ? AppletEditor.load(name)
                    : AppletEditor.forNew(name);
            editor.save();
        } catch (IOException | RuntimeException cannotLoad) {
            JOptionPane.showMessageDialog(null, "Cannot open " + name + ": " + cannotLoad.getMessage(),
                    "Applet designer", JOptionPane.ERROR_MESSAGE);
            this.frame = new JFrame();
            return;
        }

        frame = new JFrame("Applet: " + name + "   (saved as you type)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(build());
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        loadModel();
        refreshList(0);
        showIt();

        // The picture is the daemon's, so re-read it often: editing and watching is the point.
        new Timer(400, tick -> refreshPreview()).start();
        refreshPreview();
    }

    // --- the window ---------------------------------------------------------------------------

    private JPanel build() {
        final JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Applet's own settings.
        final JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        settings.add(new JLabel("title"));
        settings.add(titleField);
        settings.add(new JLabel("redraw every"));
        settings.add(intervalSpinner);
        settings.add(new JLabel("s"));
        settings.add(borderBox);
        settings.add(new JLabel("keep the screen for"));
        settings.add(followSpinner);
        settings.add(new JLabel("s after the data stops (0 = never)"));
        top.add(settings, BorderLayout.NORTH);

        // Widgets on the left, their fields on the right.
        widgetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        widgetList.setVisibleRowCount(12);
        widgetList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selected = widgetList.getSelectedIndex();
                showFieldsFor(selected);
            }
        });

        final JPanel widgetColumn = new JPanel(new BorderLayout(4, 4));
        widgetColumn.add(new JScrollPane(widgetList), BorderLayout.CENTER);
        final JPanel widgetButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        final JButton add = new JButton("Add");
        final JButton remove = new JButton("Remove");
        final JButton duplicate = new JButton("Duplicate");
        final JButton up = new JButton("\u2191");
        final JButton down = new JButton("\u2193");
        widgetButtons.add(addType);
        widgetButtons.add(add);
        widgetButtons.add(remove);
        widgetButtons.add(duplicate);
        widgetButtons.add(up);
        widgetButtons.add(down);
        widgetColumn.add(widgetButtons, BorderLayout.SOUTH);

        add.addActionListener(event -> {
            final int index = editor.addWidget(String.valueOf(addType.getSelectedItem()));
            save();
            refreshList(index);
            refreshPreview();
        });
        remove.addActionListener(event -> {
            editor.removeWidget(selected);
            save();
            refreshList(Math.min(selected, Math.max(0, editor.widgets().size() - 1)));
            refreshPreview();
        });
        duplicate.addActionListener(event -> {
            final int index = editor.duplicateWidget(selected);
            save();
            refreshList(index);
            refreshPreview();
        });
        up.addActionListener(event -> move(-1));
        down.addActionListener(event -> move(1));

        final JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JScrollPane(widgetFields), BorderLayout.CENTER);

        final JPanel aliases = new JPanel(new BorderLayout(4, 2));
        aliases.add(new JLabel("sources:  name = spec, one per line  "
                + "(e.g. ammo = json:~/.config/g13/hud.json#ammo)"), BorderLayout.NORTH);
        aliasesArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        aliases.add(new JScrollPane(aliasesArea), BorderLayout.CENTER);
        right.add(aliases, BorderLayout.SOUTH);

        final JSplitPane middle = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrap("widgets, drawn in this order", widgetColumn), wrap("the selected widget", right));
        middle.setResizeWeight(0.36);
        top.add(middle, BorderLayout.CENTER);

        // The pad's own picture of it, and how to check the design properly.
        final JPanel screen = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        screen.add(preview);
        screen.add(previewNote);
        final JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(screen, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        bottom.add(status, BorderLayout.SOUTH);
        top.add(bottom, BorderLayout.SOUTH);

        titleField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent event) { changed(); }
            @Override public void removeUpdate(final DocumentEvent event) { changed(); }
            @Override public void changedUpdate(final DocumentEvent event) { changed(); }

            private void changed() {
                if (!loading) {
                    editor.setTitle(titleField.getText());
                    save();
                }
            }
        });
        intervalSpinner.addChangeListener(event -> {
            if (!loading) {
                editor.setInterval(((Number) intervalSpinner.getValue()).doubleValue());
                save();
            }
        });
        borderBox.addActionListener(event -> {
            if (!loading) {
                editor.setBorder(borderBox.isSelected());
                save();
                refreshPreview();
            }
        });
        followSpinner.addChangeListener(event -> {
            if (!loading) {
                editor.setFollowSeconds(((Number) followSpinner.getValue()).doubleValue());
                save();
            }
        });
        aliasesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent event) { changed(); }
            @Override public void removeUpdate(final DocumentEvent event) { changed(); }
            @Override public void changedUpdate(final DocumentEvent event) { changed(); }

            private void changed() {
                if (!loading) {
                    readAliases();
                    save();
                }
            }
        });
        return top;
    }

    private JPanel wrap(final String title, final java.awt.Component inside) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(inside, BorderLayout.CENTER);
        return panel;
    }

    // --- state in and out of the fields ---------------------------------------------------------

    private void loadModel() {
        loading = true;
        titleField.setText(editor.title());
        intervalSpinner.setValue(editor.interval());
        borderBox.setSelected(editor.border());
        followSpinner.setValue(editor.followSeconds());
        final StringBuilder text = new StringBuilder();
        for (final Map.Entry<String, Object> entry : editor.sources().entrySet()) {
            text.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }
        aliasesArea.setText(text.toString());
        loading = false;
    }

    /** Reads the aliases area back, one "name = spec" per line. */
    private void readAliases() {
        final Map<String, Object> wanted = new LinkedHashMap<>();
        for (final String line : aliasesArea.getText().split("\n")) {
            final int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            final String key = line.substring(0, equals).trim();
            final String value = line.substring(equals + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                wanted.put(key, value);
            }
        }
        editor.sources().clear();
        editor.sources().putAll(wanted);
    }

    private void move(final int delta) {
        final int moved = editor.moveWidget(selected, delta);
        save();
        refreshList(moved);
        refreshPreview();
    }

    private void refreshList(final int select) {
        widgetModel.clear();
        for (int index = 0; index < editor.widgets().size(); index++) {
            widgetModel.addElement(index + ": " + editor.summary(index));
        }
        selected = editor.widgets().isEmpty() ? -1 : Math.max(0, Math.min(select, widgetModel.size() - 1));
        if (selected >= 0) {
            widgetList.setSelectedIndex(selected);
        }
        showFieldsFor(selected);
    }

    /** Rebuilds the fields for one widget: only the fields its type actually uses. */
    private void showFieldsFor(final int index) {
        widgetFields.removeAll();
        if (index < 0 || index >= editor.widgets().size()) {
            widgetFields.add(new JLabel("no widget selected"));
            widgetFields.revalidate();
            widgetFields.repaint();
            return;
        }

        loading = true;
        final Map<String, Object> widget = editor.widgets().get(index);
        final String type = String.valueOf(widget.getOrDefault("type", "text"));

        int row = 0;
        final JComboBox<String> typeBox = new JComboBox<>(AppletEditor.TYPES.toArray(new String[0]));
        typeBox.setSelectedItem(type);
        typeBox.addActionListener(event -> {
            if (!loading) {
                editor.set(index, "type", String.valueOf(typeBox.getSelectedItem()));
                save();
                refreshList(index);
                refreshPreview();
            }
        });
        addRow(row++, "type", typeBox);

        final List<String> fields = new ArrayList<>(List.of("x", "y"));
        fields.addAll(AppletEditor.FIELDS.getOrDefault(type, List.of()));
        for (final String name : fields) {
            final Object value = widget.get(name);
            if ("scroll".equals(name)) {
                final JCheckBox box = new JCheckBox();
                box.setSelected(Boolean.TRUE.equals(value));
                box.addActionListener(event -> field(index, name, box.isSelected()));
                addRow(row++, name, box);
            } else if ("align".equals(name)) {
                final JComboBox<String> box = new JComboBox<>(new String[]{"left", "centre", "right"});
                box.setSelectedItem(value == null ? "left" : String.valueOf(value));
                box.addActionListener(event -> field(index, name, box.getSelectedItem()));
                addRow(row++, name, box);
            } else if (NUMERIC.contains(name)) {
                final JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                        value instanceof Number ? ((Number) value).doubleValue() : 0.0,
                        -200.0, 1000.0, 1.0));
                spinner.addChangeListener(event -> field(index, name,
                        ((Number) spinner.getValue()).doubleValue()));
                addRow(row++, name, spinner);
            } else {
                final JTextField text = new JTextField(value == null ? "" : String.valueOf(value), 18);
                text.getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(final DocumentEvent event) { changed(); }
                    @Override public void removeUpdate(final DocumentEvent event) { changed(); }
                    @Override public void changedUpdate(final DocumentEvent event) { changed(); }

                    private void changed() {
                        if (!loading) {
                            field(index, name, text.getText());
                            widgetModel.set(index, index + ": " + editor.summary(index));
                        }
                    }
                });
                addRow(row++, name, text);
            }
        }
        loading = false;

        widgetFields.revalidate();
        widgetFields.repaint();
    }

    private void addRow(final int row, final String label, final java.awt.Component editor1) {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 4, 2, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = row;
        widgetFields.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        widgetFields.add(editor1, constraints);
    }

    /** One field changed: write it to the file, then show what the pad makes of it. */
    private void field(final int index, final String name, final Object value) {
        if (value instanceof Boolean || NUMERIC.contains(name)) {
            // A checkbox has to stay a real boolean: the string "false" reads as true to the
            // daemon, which would leave the text scrolling when it was switched off.
            editor.set(index, name, value);
        } else {
            editor.set(index, name, String.valueOf(value));
        }
        save();
        refreshPreview();
    }

    // --- writing, showing and the picture -------------------------------------------------------

    private void save() {
        try {
            editor.save();
            status.setText("saved   ·   check the design with:  g13-applet check "
                    + AppletEditor.path(appletName));
        } catch (IOException e) {
            status.setText("cannot write " + AppletEditor.path(appletName) + ": " + e.getMessage());
        }
    }

    /** Asks the daemon to show this applet, so editing and seeing are the same act. */
    private void showIt() {
        try {
            final Visuals visuals = Visuals.load();
            final String key = Visuals.APPLET_PREFIX + appletName;
            if (!visuals.enabled().contains(key)) {
                visuals.enabled().add(key);
            }
            visuals.setActive(key);
            visuals.save();
            previewNote.setText("the pad is showing this applet while you edit it");
        } catch (IOException e) {
            previewNote.setText("cannot ask the daemon to show it: " + e.getMessage());
        }
    }

    private void refreshPreview() {
        final java.nio.file.Path published = runtimePath("g13-screen");
        try {
            preview.show(Files.readAllLines(published));
            if (!previewNote.getText().startsWith("cannot")) {
                previewNote.setText("the pad is showing this applet while you edit it"
                        + (previewHasOverlaps() ? "   ·   text is landing on ink (in orange)" : ""));
            }
        } catch (IOException e) {
            preview.showMessage("no screen published - is g13-visuals running?");
            previewNote.setText("start it with:  systemctl --user start g13-visuals");
        }
    }

    /** Whether any text is landing on ink, which the preview marks in orange. */
    private boolean previewHasOverlaps() {
        final boolean[][] marked = preview.overlaps();
        if (marked == null) {
            return false;
        }
        for (final boolean[] column : marked) {
            for (final boolean cell : column) {
                if (cell) {
                    return true;
                }
            }
        }
        return false;
    }

    private static java.nio.file.Path runtimePath(final String name) {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isEmpty()) {
            return java.nio.file.Paths.get(runtime, name);
        }
        final java.nio.file.Path perUser =
                java.nio.file.Paths.get("/run/user", String.valueOf(uid()));
        return (Files.isDirectory(perUser) ? perUser : java.nio.file.Paths.get("/tmp")).resolve(name);
    }

    private static int uid() {
        try {
            final Object value = Files.getAttribute(java.nio.file.Paths.get("/proc/self"), "unix:uid");
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException noProc) {
            // No /proc: fall back to whatever /run/user holds.
            try (java.nio.file.DirectoryStream<java.nio.file.Path> entries =
                         Files.newDirectoryStream(java.nio.file.Paths.get("/run/user"))) {
                for (final java.nio.file.Path entry : entries) {
                    return Integer.parseInt(entry.getFileName().toString());
                }
            } catch (IOException | NumberFormatException none) {
                return 1000;
            }
        }
        return 1000;
    }
}
