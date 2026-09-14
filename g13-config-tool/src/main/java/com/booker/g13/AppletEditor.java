package com.booker.g13;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One applet being edited: its title and interval, its source aliases, and its widgets.
 *
 * The file is the truth, and it is written on every change — there is no Apply button, and no
 * unsaved state to lose. The daemon watches the applets directory, so the pad picks a change up
 * within a couple of seconds.
 *
 * This deliberately does not validate a design. The one authority on whether an applet works is
 * {@code g13-applet check} in the repository: it draws the screen and knows the rules the pad has
 * (text must land on blank pixels, a format string has to be readable). A second set of rules here
 * could disagree with it, which would be worse than none.
 */
public class AppletEditor {

    /** What each widget type uses, so the window only offers fields that mean something. */
    public static final Map<String, List<String>> FIELDS = new LinkedHashMap<>();

    /** The widget types, in the order the window adds them. */
    public static final List<String> TYPES = Arrays.asList(
            "text", "bar", "segments", "brackets", "arrow", "turn", "line", "vline", "box");

    /** Applet names that are safe as file names. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,40}");

    static {
        FIELDS.put("text", List.of("format", "source", "align", "margin", "scroll",
                "scroll_width", "scroll_speed"));
        FIELDS.put("bar", List.of("source", "max", "w", "h"));
        FIELDS.put("segments", List.of("source", "max", "w", "h", "count"));
        FIELDS.put("brackets", List.of("w", "h", "len", "thick"));
        FIELDS.put("arrow", List.of("source", "r"));
        FIELDS.put("turn", List.of("source", "size"));
        FIELDS.put("line", List.of("w"));
        FIELDS.put("vline", List.of("h"));
        FIELDS.put("box", List.of("w", "h"));
    }

    private final String name;
    private final Map<String, Object> definition;

    private AppletEditor(final String name, final Map<String, Object> definition) {
        this.name = name;
        this.definition = definition;
    }

    /**
     * Reads an applet from the applets directory.
     * @param name The file stem, without .json.
     * @return the editor.
     * @throws IOException when the file cannot be read.
     */
    public static AppletEditor load(final String name) throws IOException {
        final Map<String, Object> definition = Json.parseObject(Files.readString(path(name)));
        definition.putIfAbsent("name", name);
        definition.putIfAbsent("title", name.toUpperCase());
        final List<Object> widgets = new ArrayList<>();
        if (definition.get("widgets") instanceof List) {
            widgets.addAll((List<?>) definition.remove("widgets"));
        }
        definition.put("widgets", widgets);
        return new AppletEditor(name, definition);
    }

    /**
     * A new applet that already draws something: one line of text, well clear of the frame.
     * @param name The file stem.
     * @return the editor.
     */
    public static AppletEditor forNew(final String name) {
        final Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", name);
        definition.put("title", name.toUpperCase());
        definition.put("border", true);
        definition.put("interval", 1.0);
        definition.put("sources", new LinkedHashMap<String, Object>());
        definition.put("widgets", new ArrayList<Object>());
        final AppletEditor editor = new AppletEditor(name, definition);
        final Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "text");
        text.put("x", 3.0);
        text.put("y", 20.0);
        text.put("format", "CPU {cpu:.0f}%");
        editor.widgets().add(text);
        return editor;
    }

    /**
     * The applets in the applets directory.
     * @return their names, sorted.
     */
    public static List<String> names() {
        final List<String> found = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(Visuals.appletDir(), "*.json")) {
            for (final Path entry : entries) {
                final String file = entry.getFileName().toString();
                found.add(file.substring(0, file.length() - ".json".length()));
            }
        } catch (IOException | RuntimeException none) {
            return found;
        }
        found.sort(String::compareTo);
        return found;
    }

    /**
     * Whether a name can be a file name.
     * @param name The name to check.
     * @return true when it is lowercase letters, digits and hyphens.
     */
    public static boolean validName(final String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /**
     * Where an applet lives.
     * @param name The file stem.
     * @return the path.
     */
    public static Path path(final String name) {
        return Visuals.appletDir().resolve(name + ".json");
    }

    /**
     * @return the applet's name.
     */
    public String name() {
        return name;
    }

    /**
     * @return the title as it appears on the pad.
     */
    public String title() {
        return String.valueOf(definition.getOrDefault("title", name.toUpperCase()));
    }

    /**
     * @param value The new title.
     */
    public void setTitle(final String value) {
        definition.put("title", value);
    }

    /**
     * @return how often the daemon redraws it, in seconds.
     */
    public double interval() {
        return number(definition.get("interval"), 1.0);
    }

    /**
     * @param seconds The new interval, kept at a fifth of a second or more.
     */
    public void setInterval(final double seconds) {
        definition.put("interval", Math.max(0.2, seconds));
    }

    /**
     * @return whether it draws the frame and title.
     */
    public boolean border() {
        return !Boolean.FALSE.equals(definition.get("border"));
    }

    /**
     * @param value Whether to draw the frame and title.
     */
    public void setBorder(final boolean value) {
        definition.put("border", value);
    }

    /**
     * @return how long it keeps the screen after its data stops, or 0 for never.
     */
    public double followSeconds() {
        final Object follow = definition.get("follow");
        if (follow instanceof Map) {
            return number(((Map<?, ?>) follow).get("seconds"), 20.0);
        }
        return 0;
    }

    /**
     * @param seconds How long it keeps the screen after its data stops; 0 removes the behaviour.
     */
    public void setFollowSeconds(final double seconds) {
        if (seconds <= 0) {
            definition.remove("follow");
        } else {
            final Map<String, Object> follow = new LinkedHashMap<>();
            follow.put("seconds", seconds);
            definition.put("follow", follow);
        }
    }

    /**
     * @return the source aliases, name to spec.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sources() {
        if (!(definition.get("sources") instanceof Map)) {
            definition.put("sources", new LinkedHashMap<String, Object>());
        }
        return (Map<String, Object>) definition.get("sources");
    }

    /**
     * @return the widgets, in the order the daemon draws them.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> widgets() {
        if (!(definition.get("widgets") instanceof List)) {
            definition.put("widgets", new ArrayList<Object>());
        }
        return (List<Map<String, Object>>) (List<?>) definition.get("widgets");
    }

    /**
     * Adds a widget of a type, in a place that will not land on the frame.
     * @param type One of TYPES.
     * @return the new widget's index.
     */
    public int addWidget(final String type) {
        final Map<String, Object> widget = new LinkedHashMap<>();
        widget.put("type", type);
        widget.put("x", 3.0);
        widget.put("y", 12.0);
        if (FIELDS.containsKey(type)) {
            for (final String field : FIELDS.get(type)) {
                switch (field) {
                    case "w": widget.put("w", 88.0); break;
                    case "h": widget.put("h", 8.0); break;
                    case "max": widget.put("max", 100.0); break;
                    case "count": widget.put("count", 8.0); break;
                    case "source": widget.put("source", "cpu"); break;
                    case "format": widget.put("format", "{cpu:.0f}%"); break;
                    case "r":
                    case "size": widget.put(field, 6.0); break;
                    case "len": widget.put("len", 4.0); break;
                    case "thick": widget.put("thick", 2.0); break;
                    default: break;
                }
            }
        }
        widgets().add(widget);
        return widgets().size() - 1;
    }

    /**
     * Removes a widget.
     * @param index Its position.
     */
    public void removeWidget(final int index) {
        if (index >= 0 && index < widgets().size()) {
            widgets().remove(index);
        }
    }

    /**
     * Copies a widget, one row below it.
     * @param index Its position.
     * @return the copy's position.
     */
    public int duplicateWidget(final int index) {
        if (index < 0 || index >= widgets().size()) {
            return index;
        }
        final Map<String, Object> copy = new LinkedHashMap<>(widgets().get(index));
        copy.put("y", number(copy.get("y"), 0) + 9);
        widgets().add(index + 1, copy);
        return index + 1;
    }

    /**
     * Moves a widget up or down the drawing order.
     * @param index Its position.
     * @param delta -1 or +1.
     * @return where it ended up.
     */
    public int moveWidget(final int index, final int delta) {
        final int target = index + delta;
        if (index < 0 || index >= widgets().size() || target < 0 || target >= widgets().size()) {
            return index;
        }
        widgets().add(target, widgets().remove(index));
        return target;
    }

    /**
     * Sets one field of one widget, or removes it when the value is null or empty.
     * @param index The widget.
     * @param field The field name.
     * @param value The value.
     */
    public void set(final int index, final String field, final Object value) {
        if (index < 0 || index >= widgets().size()) {
            return;
        }
        if (value == null || "".equals(value)) {
            widgets().get(index).remove(field);
        } else {
            widgets().get(index).put(field, value);
        }
    }

    /**
     * One line describing a widget, for the list in the window.
     * @param index The widget.
     * @return the summary.
     */
    public String summary(final int index) {
        final Map<String, Object> widget = widgets().get(index);
        final StringBuilder line = new StringBuilder(String.valueOf(widget.getOrDefault("type",
                "text")));
        if (widget.get("format") != null) {
            line.append("  \"").append(widget.get("format")).append('"');
        } else if (widget.get("source") != null) {
            line.append("  ").append(widget.get("source"));
        }
        line.append("   @ ").append(trimmed(number(widget.get("x"), 0)))
                .append(',').append(trimmed(number(widget.get("y"), 0)));
        if (widget.get("w") != null || widget.get("h") != null) {
            line.append("   ").append(trimmed(number(widget.get("w"), 0))).append('x')
                    .append(trimmed(number(widget.get("h"), 0)));
        }
        return line.toString();
    }

    /**
     * Writes the applet out. Called on every change: there is nothing unsaved to lose.
     * @throws IOException when it cannot be written.
     */
    public void save() throws IOException {
        Files.createDirectories(Visuals.appletDir());
        Files.writeString(path(name), Json.write(definition));
    }

    /**
     * Removes the applet's file.
     * @throws IOException when it cannot be removed.
     */
    public void delete() throws IOException {
        Files.deleteIfExists(path(name));
    }

    private static double number(final Object value, final double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static String trimmed(final double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
