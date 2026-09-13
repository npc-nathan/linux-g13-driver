package com.booker.g13;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The visuals configuration: which visuals exist, which are enabled and in what order,
 * which one is showing, and whether they cycle.
 *
 * The same file the g13-visuals daemon reads, $XDG_CONFIG_HOME/g13/visuals.json. The daemon
 * notices a change within a second, so editing here takes effect without a restart.
 */
public class Visuals {

    /** The visuals that ship with the daemon, in their default order. */
    public static final String[] BUILT_IN = {"clock", "system", "media", "pad", "custom"};

    /** Prefix the daemon gives a designed applet. */
    public static final String APPLET_PREFIX = "applet:";

    /** Enabled visuals, in the order they cycle. */
    private final List<String> enabled = new ArrayList<>();

    /** The visual being shown. */
    private String active = BUILT_IN[0];

    /** Whether the daemon steps through them on its own. */
    private boolean cycle;

    /** How long each is shown when cycling. */
    private double cycleSeconds = 10;

    // --- where things live ---

    /**
     * @return The driver's config directory.
     */
    public static Path configDir() {
        final String base = System.getenv("XDG_CONFIG_HOME");
        if (base != null && !base.isBlank()) {
            return Paths.get(base, "g13");
        }
        return Paths.get(System.getProperty("user.home"), ".config", "g13");
    }

    /**
     * @return The directory designed applets live in.
     */
    public static Path appletDir() {
        return configDir().resolve("applets");
    }

    /**
     * @return The file this configuration lives in.
     */
    public static Path configFile() {
        return configDir().resolve("visuals.json");
    }

    // --- loading and saving ---

    /**
     * Reads the configuration, falling back to the built-in visuals.
     * @return The configuration.
     */
    public static Visuals load() {
        final Visuals visuals = new Visuals();
        final Path file = configFile();

        try {
            if (Files.exists(file)) {
                final String text = Files.readString(file);
                visuals.enabled.addAll(stringsIn(text, "enabled"));
                final String activeValue = stringAfter(text, "active");
                if (activeValue != null) {
                    visuals.active = activeValue;
                }
                visuals.cycle = Boolean.parseBoolean(valueAfter(text, "cycle"));
                final String seconds = valueAfter(text, "cycle_seconds");
                if (seconds != null) {
                    try {
                        visuals.cycleSeconds = Double.parseDouble(seconds);
                    } catch (NumberFormatException e) {
                        // Keep the default.
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }

        if (visuals.enabled.isEmpty()) {
            visuals.enabled.addAll(List.of(BUILT_IN));
        }
        if (!visuals.known().containsKey(visuals.active)) {
            visuals.active = visuals.enabled.get(0);
        }
        return visuals;
    }

    /**
     * Writes the file the daemon reads.
     * @throws IOException if it cannot be written.
     */
    public void save() throws IOException {
        final Path file = configFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson());
    }

    /**
     * @return The file contents as JSON.
     */
    public String toJson() {
        final StringBuilder text = new StringBuilder();
        text.append("{\n  \"enabled\": [");
        for (int index = 0; index < enabled.size(); index++) {
            text.append(index == 0 ? "\n    " : ",\n    ").append(quote(enabled.get(index)));
        }
        text.append(enabled.isEmpty() ? "],\n" : "\n  ],\n");
        text.append("  \"active\": ").append(quote(active)).append(",\n");
        text.append("  \"cycle\": ").append(cycle).append(",\n");
        text.append("  \"cycle_seconds\": ").append(cycleSeconds).append("\n}\n");
        return text.toString();
    }

    // --- the visuals that exist ---

    /**
     * Every visual the daemon can show: the built-ins plus whatever applets are on disk.
     * @return Names to their display titles.
     */
    public Map<String, String> known() {
        final Map<String, String> known = new LinkedHashMap<>();
        for (final String name : BUILT_IN) {
            known.put(name, name);
        }
        known.putAll(applets());
        return known;
    }

    /**
     * @return The designed applets on disk: name to title.
     */
    public static Map<String, String> applets() {
        final Map<String, String> applets = new LinkedHashMap<>();
        final Path directory = appletDir();
        if (!Files.isDirectory(directory)) {
            return applets;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (final Path path : stream) {
                final String text = Files.readString(path);
                final String title = stringAfter(text, "title");
                final String name = path.getFileName().toString().replace(".json", "");
                applets.put(APPLET_PREFIX + name, title == null ? name : title);
            }
        } catch (IOException e) {
            System.err.println("Could not list " + directory + ": " + e.getMessage());
        }
        return applets;
    }

    // --- accessors ---

    /**
     * @return The enabled visuals, in order.
     */
    public List<String> enabled() {
        return enabled;
    }

    /**
     * @return The visual being shown.
     */
    public String active() {
        return active;
    }

    /**
     * @param name The visual to show.
     */
    public void setActive(final String name) {
        active = name;
    }

    /**
     * @return true when the daemon cycles through the visuals.
     */
    public boolean cycling() {
        return cycle;
    }

    /**
     * @param value Whether to cycle.
     */
    public void setCycling(final boolean value) {
        cycle = value;
    }

    /**
     * @return Seconds each visual is shown when cycling.
     */
    public double cycleSeconds() {
        return cycleSeconds;
    }

    /**
     * @param seconds Seconds each visual is shown when cycling.
     */
    public void setCycleSeconds(final double seconds) {
        cycleSeconds = seconds;
    }

    // --- tiny JSON helpers, so the tool needs no JSON library ---

    private static String quote(final String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    /**
     * @param text The JSON text.
     * @param key The key to find.
     * @return The string value, or null.
     */
    static String stringAfter(final String text, final String key) {
        final String raw = valueAfter(text, key);
        if (raw == null || !raw.startsWith("\"")) {
            return null;
        }
        return raw.substring(1, raw.length() - 1);
    }

    /**
     * @param text The JSON text.
     * @param key The key to find.
     * @return Everything after the colon up to the comma or brace, or null.
     */
    static String valueAfter(final String text, final String key) {
        final int at = text.indexOf("\"" + key + "\"");
        if (at < 0) {
            return null;
        }
        final int colon = text.indexOf(':', at);
        if (colon < 0) {
            return null;
        }
        int end = colon + 1;
        while (end < text.length() && " \t\n\r".indexOf(text.charAt(end)) >= 0) {
            end++;
        }
        int stop = end;
        while (stop < text.length() && ",}] \t\n\r".indexOf(text.charAt(stop)) < 0) {
            stop++;
        }
        return text.substring(end, stop);
    }

    /**
     * @param text The JSON text.
     * @param key The key whose array to read.
     * @return The strings in that array.
     */
    static List<String> stringsIn(final String text, final String key) {
        final List<String> values = new ArrayList<>();
        final int at = text.indexOf("\"" + key + "\"");
        if (at < 0) {
            return values;
        }
        final int start = text.indexOf('[', at);
        final int end = text.indexOf(']', start);
        if (start < 0 || end < 0) {
            return values;
        }

        int index = start + 1;
        while (index < end) {
            final int open = text.indexOf('"', index);
            if (open < 0 || open >= end) {
                break;
            }
            final int close = text.indexOf('"', open + 1);
            if (close < 0) {
                break;
            }
            values.add(text.substring(open + 1, close));
            index = close + 1;
        }
        return values;
    }
}
