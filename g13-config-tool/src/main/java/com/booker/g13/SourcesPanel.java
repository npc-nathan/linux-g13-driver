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

    /** Opens the window. */
    public static void showWindow() {
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

        window.setContentPane(content);
        window.setPreferredSize(new Dimension(620, 380));
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
