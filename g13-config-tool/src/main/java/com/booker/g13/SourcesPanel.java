package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/**
 * The sources panel: what data an applet may read, and what is switched off.
 *
 * One switch per kind of source. An applet is a JSON file that can be passed around, so this is
 * the place a capability is granted or taken away - and the reason it is a screen rather than a
 * config key is that a permission nobody can see is not much of a permission.
 *
 * There are no confirm buttons: a tick writes {@code sources.json} at once and the daemon picks it
 * up within a couple of seconds. A kind that is switched off reads as an empty value in every
 * applet, so nothing breaks and nothing has to be restarted.
 */
public class SourcesPanel {

    /** The published values, and how long ago they were written. */
    private static Path publishedFile() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isEmpty()) {
            return Paths.get(runtime, "g13-values.json");
        }
        // No XDG_RUNTIME_DIR - a command started by something that does not inherit the login
        // environment - so look where the services put it, not in /tmp, or a daemon that is
        // plainly drawing the screen is reported as absent.
        final Path perUser = Paths.get("/run/user", Integer.toString(userId()));
        final Path base = Files.isDirectory(perUser) ? perUser : Paths.get("/tmp");
        return base.resolve("g13-values.json");
    }

    /**
     * The current user's id, as far as the filesystem will say. Java has no uid of its own.
     * @return the uid, or a sensible guess if /proc is not there.
     */
    private static int userId() {
        try {
            final Object uid = Files.getAttribute(Paths.get("/proc/self"), "unix:uid");
            if (uid instanceof Integer) {
                return (Integer) uid;
            }
        } catch (Exception unsupported) {
            // No /proc, so not Linux: fall through and see what /run/user holds.
        }
        try (var entries = Files.list(Paths.get("/run/user"))) {
            final java.util.List<Path> found = entries.limit(2).toList();
            if (found.size() == 1) {
                return Integer.parseInt(found.get(0).getFileName().toString());
            }
        } catch (Exception none) {
            // Nothing to learn there either.
        }
        return 1000;
    }

    /** The named web addresses: where a token lives, and a way to prove one works. */
    private JPanel endpointsTab() {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        for (final String name : endpoints.keySet()) {
            endpointNames.addElement(name);
        }
        endpointList.setVisibleRowCount(8);
        endpointList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        endpointList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showEndpoint(endpointList.getSelectedValue());
            }
        });

        final JPanel listColumn = new JPanel(new BorderLayout(4, 4));
        listColumn.add(new JLabel("addresses an applet may name"), BorderLayout.NORTH);
        listColumn.add(new javax.swing.JScrollPane(endpointList), BorderLayout.CENTER);
        final JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        final JButton add = new JButton("Add\u2026");
        final JButton remove = new JButton("Remove");
        listButtons.add(add);
        listButtons.add(remove);
        listColumn.add(listButtons, BorderLayout.SOUTH);

        final JPanel fields = new JPanel(new GridBagLayout());
        final GridBagConstraints layout = new GridBagConstraints();
        layout.anchor = GridBagConstraints.WEST;
        layout.insets = new Insets(3, 3, 3, 8);
        int row = 0;

        layout.gridx = 0;
        layout.gridy = row;
        fields.add(new JLabel("address"), layout);
        layout.gridx = 1;
        layout.weightx = 1;
        layout.fill = GridBagConstraints.HORIZONTAL;
        fields.add(address, layout);
        layout.weightx = 0;
        layout.fill = GridBagConstraints.NONE;

        layout.gridx = 0;
        layout.gridy = ++row;
        fields.add(new JLabel("token"), layout);
        layout.gridx = 1;
        final JPanel tokenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tokenRow.add(token);
        tokenRow.add(showToken);
        fields.add(tokenRow, layout);

        layout.gridx = 0;
        layout.gridy = ++row;
        fields.add(new JLabel("timeout"), layout);
        layout.gridx = 1;
        final JPanel timeoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timeoutRow.add(timeout);
        timeoutRow.add(new JLabel("seconds (0 = the daemon's own)"));
        fields.add(timeoutRow, layout);

        layout.gridx = 1;
        layout.gridy = ++row;
        fields.add(insecure, layout);

        layout.gridx = 1;
        layout.gridy = ++row;
        fields.add(new JLabel("sent as  Authorization: Bearer <token>; an applet writes "
                + "http:<name>/<path>#<field>"), layout);

        // A way to prove an address works, using the daemon's own reader rather than a second one.
        layout.gridx = 0;
        layout.gridy = ++row;
        fields.add(new JLabel("read"), layout);
        layout.gridx = 1;
        final JPanel readRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        readPath.setToolTipText("path and field, e.g. api/states/sensor.outside#state");
        final JButton test = new JButton("Test");
        test.setToolTipText("Read it with g13-visuals --read, the same reader the pad uses");
        test.addActionListener(event -> testEndpoint());
        readRow.add(readPath);
        readRow.add(test);
        fields.add(readRow, layout);

        endpointStatus.setEditable(false);
        endpointStatus.setOpaque(false);
        endpointStatus.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        final JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(fields, BorderLayout.NORTH);
        right.add(endpointStatus, BorderLayout.CENTER);

        address.getDocument().addDocumentListener(saver());
        token.getDocument().addDocumentListener(saver());
        showToken.addActionListener(event ->
                token.setEchoChar(showToken.isSelected() ? (char) 0 : '\u2022'));
        timeout.addChangeListener(event -> saveEndpoints());
        insecure.addActionListener(event -> saveEndpoints());
        add.addActionListener(event -> addEndpoint());
        remove.addActionListener(event -> removeEndpoint());

        final javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, listColumn, right);
        split.setResizeWeight(0.32);
        panel.add(split, BorderLayout.CENTER);
        panel.add(new JLabel("The token is kept in " + Sources.endpointsFile()
                + ", written readable only by you: an applet carries the endpoint's name, never "
                + "the credential."), BorderLayout.SOUTH);
        return panel;
    }

    /** A listener for the two text fields: written as you type, as everywhere else in here. */
    private javax.swing.event.DocumentListener saver() {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(final javax.swing.event.DocumentEvent event) {
                saveEndpoints();
            }

            @Override
            public void removeUpdate(final javax.swing.event.DocumentEvent event) {
                saveEndpoints();
            }

            @Override
            public void changedUpdate(final javax.swing.event.DocumentEvent event) {
                saveEndpoints();
            }
        };
    }

    private void showEndpoint(final String name) {
        final Sources.Endpoint endpoint = endpoints.get(name);
        if (endpoint == null) {
            return;
        }
        loading = true;
        address.setText(endpoint.url);
        token.setText(endpoint.token);
        timeout.setValue(endpoint.timeout);
        insecure.setSelected(endpoint.insecure);
        loading = false;
        if (readPath.getText().isEmpty()) {
            readPath.setText("#state");
        }
        endpointStatus.setText(specHint(name, readPath.getText()));
    }

    private void saveEndpoints() {
        if (loading) {
            return;
        }
        final String name = endpointList.getSelectedValue();
        final Sources.Endpoint endpoint = endpoints.get(name);
        if (endpoint == null) {
            return;
        }
        endpoint.url = address.getText().trim();
        endpoint.token = new String(token.getPassword());
        endpoint.timeout = ((Number) timeout.getValue()).doubleValue();
        endpoint.insecure = insecure.isSelected();
        try {
            Sources.saveEndpoints(endpoints);
            endpointStatus.setText("saved   \u00b7   " + specHint(name, readPath.getText())
                    + "   \u00b7   " + Sources.endpointsFile());
        } catch (RuntimeException error) {
            endpointStatus.setText("could not write " + Sources.endpointsFile() + ": "
                    + error.getMessage());
        }
    }

    /** The line an applet would carry, so it can be copied rather than worked out. */
    private static String specHint(final String name, final String path) {
        return "an applet writes:  http:" + name + "/" + (path.isEmpty() ? "#field" : path);
    }

    private void addEndpoint() {
        final String name = javax.swing.JOptionPane.showInputDialog(null,
                "Name for this address (letters, digits, hyphens):", "");
        if (name == null || name.isBlank()) {
            return;
        }
        final String clean = name.trim();
        if (!Sources.validEndpointName(clean)) {
            endpointStatus.setText("'" + clean + "' cannot be an endpoint name");
            return;
        }
        endpoints.putIfAbsent(clean, new Sources.Endpoint());
        saveEndpoints();
        if (!endpointNames.contains(clean)) {
            endpointNames.addElement(clean);
        }
        endpointList.setSelectedValue(clean, true);
    }

    private void removeEndpoint() {
        final String name = endpointList.getSelectedValue();
        if (name == null || !endpoints.containsKey(name)) {
            return;
        }
        endpoints.remove(name);
        endpointNames.removeElement(name);
        saveEndpoints();
        endpointStatus.setText(name + " is gone. An applet naming it now reads an empty value.");
    }

    /** Reads one address with the daemon's own reader, so a test here means what the pad means. */
    private void testEndpoint() {
        final String name = endpointList.getSelectedValue();
        if (name == null) {
            endpointStatus.setText("pick or add an address first");
            return;
        }
        saveEndpoints();
        endpointStatus.setText("reading " + name + " \u2026\n" + Installed.run(
                Installed.command("g13-visuals"), "--read",
                "http:" + name + "/" + readPath.getText().trim()));
    }

    /**
     * What the daemon last published, or null when it is not running.
     * @return the file's text.
     */
    public static String publishedText() {
        try {
            return Files.readString(publishedFile());
        } catch (Exception absent) {
            return null;
        }
    }

    /**
     * One published value, for showing that a source is alive.
     * @param key The published name.
     * @return its value, or null.
     */
    public static String published(final String key) {
        final String text = publishedText();
        if (text == null) {
            return null;
        }
        final Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\":\\s*\"?([^\",\\n]*)")
                .matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** The named web addresses being edited. */
    private final java.util.Map<String, Sources.Endpoint> endpoints =
            new java.util.LinkedHashMap<>(Sources.endpoints());

    private final javax.swing.DefaultListModel<String> endpointNames =
            new javax.swing.DefaultListModel<>();

    private final javax.swing.JList<String> endpointList = new javax.swing.JList<>(endpointNames);

    private final javax.swing.JTextField address = new javax.swing.JTextField(28);
    private final javax.swing.JPasswordField token = new javax.swing.JPasswordField(22);
    private final javax.swing.JCheckBox showToken = new javax.swing.JCheckBox("show");
    private final javax.swing.JSpinner timeout =
            new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(0.0, 0.0, 120.0, 0.5));
    private final javax.swing.JCheckBox insecure = new javax.swing.JCheckBox(
            "accept a certificate that does not check out (a local server)");
    private final javax.swing.JTextField readPath = new javax.swing.JTextField(24);
    private final javax.swing.JTextArea endpointStatus = new javax.swing.JTextArea(5, 40);

    /** True while the fields are being filled in, so their listeners do not write back. */
    private boolean loading;

    /** Opens the window. */
    public static void showWindow() {
        new SourcesPanel().open();
    }

    private void open() {
        final JFrame window = new JFrame("G13 sources");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        final JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        final JPanel rows = new JPanel(new GridBagLayout());
        final JLabel result = new JLabel(" ");
        final JLabel readings = new JLabel(" ");

        final GridBagConstraints layout = new GridBagConstraints();
        layout.anchor = GridBagConstraints.WEST;
        layout.insets = new Insets(3, 3, 3, 8);
        layout.gridy = 0;

        for (final Sources.Kind kind : Sources.KINDS) {
            final JCheckBox box = new JCheckBox(kind.name, Sources.enabled(kind.name));
            box.setToolTipText("Applets may read " + kind.explanation);
            box.addActionListener(event -> {
                try {
                    Sources.setEnabled(kind.name, box.isSelected());
                    result.setText(kind.name + " is " + (box.isSelected()
                            ? "switched on: applets may read it again"
                            : "switched off: applets reading it now get an empty value"));
                    readings.setText(readingsText());
                } catch (RuntimeException error) {
                    result.setText("could not write " + Sources.configFile() + ": "
                            + error.getMessage());
                    box.setSelected(Sources.enabled(kind.name));
                }
            });

            layout.gridx = 0;
            rows.add(box, layout);
            final JLabel what = new JLabel(kind.explanation);
            what.setToolTipText(kind.explanation);
            layout.gridx = 1;
            layout.weightx = 1;
            rows.add(what, layout);
            layout.weightx = 0;
            layout.gridy++;
        }

        final JTextArea note = new JTextArea(
            "An applet is a JSON file, and it can be passed around, so this is where a capability\n"
            + "is granted. Everything is switched on until you turn it off, and a switched-off kind\n"
            + "reads as an empty value rather than an error, so no applet breaks - it just has\n"
            + "nothing to show. Somebody else's applet is worth checking before you switch a kind\n"
            + "back on for it: g13-applet check FILE says which applet is asking for what.");
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));

        final JButton again = new JButton("Read again");
        again.setToolTipText("Ask the daemon what these read right now");
        again.addActionListener(event -> readings.setText(readingsText()));

        final JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(rows);
        top.add(note);

        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        footer.add(again);
        footer.add(readings);

        content.add(top, BorderLayout.NORTH);
        content.add(result, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        readings.setHorizontalAlignment(SwingConstants.LEFT);
        readings.setText(readingsText());
        result.setText(" ");

        final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Kinds", content);
        tabs.addTab("Endpoints", endpointsTab());
        window.setContentPane(tabs);
        window.setPreferredSize(new Dimension(660, 420));
        window.pack();
        window.setLocationByPlatform(true);
        window.setVisible(true);
    }

    /**
     * A line showing that a source really is reading something, straight from what the daemon
     * published, so the panel is not a list of switches with nothing behind them.
     * @return the line.
     */
    public static String readingsText() {
        if (publishedText() == null) {
            return "nothing published yet (" + publishedFile() + "): the daemon is not running";
        }
        final StringBuilder line = new StringBuilder("right now:  ");
        for (final String key : new String[] {"cpu", "memory", "time", "date"}) {
            final String value = published(key);
            if (value != null && !value.isEmpty()) {
                line.append(key).append(' ').append(value).append("   ");
            }
        }
        return line.toString().trim().isEmpty() ? "nothing published yet" : line.toString();
    }
}
