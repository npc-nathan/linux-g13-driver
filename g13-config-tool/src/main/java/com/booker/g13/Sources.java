package com.booker.g13;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What data an applet may read: the kinds of source, and which are switched off.
 *
 * The same file the daemon enforces, {@code $XDG_CONFIG_HOME/g13/sources.json}. An applet is a
 * JSON file somebody may have been given, so this is the permission model: a kind that is
 * switched off reads as an empty value in every applet rather than failing, and
 * {@code g13-applet check} says which applet is asking for it.
 *
 * The list of kinds is kept in step with the daemon's own list by a test in {@code tests/},
 * because a kind offered here that the daemon did not know would write a switch that does
 * nothing at all.
 */
public class Sources {

    /** A kind of source: what switching it on lets an applet do. */
    public static final class Kind {
        /** The name used in a source, before the colon. */
        public final String name;
        /** What it lets an applet read, in plain words. */
        public final String explanation;

        Kind(final String name, final String explanation) {
            this.name = name;
            this.explanation = explanation;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** The kinds, in the order the panel shows them. */
    public static final Kind[] KINDS = {
        new Kind("built-in",
                 "the machine's own numbers: cpu, memory, uptime, time, media, profile, keys"),
        new Kind("env", "environment variables, e.g. env:HOME"),
        new Kind("file", "the first line of any file, e.g. file:/proc/loadavg"),
        new Kind("json", "named fields out of a JSON file, e.g. json:/path/to/hud.json#health"),
        new Kind("cmd", "shell commands, e.g. cmd:date +%H:%M"),
    };

    /** The file the daemon reads. */
    public static Path configFile() {
        return Visuals.configDir().resolve("sources.json");
    }

    /**
     * The kinds that are switched off.
     * @return their names, in the order the file lists them.
     */
    public static List<String> disabled() {
        final List<String> found = new ArrayList<>();
        try {
            final String text = Files.readString(configFile());
            final Matcher matcher = Pattern.compile("\"([a-z\\-]+)\"").matcher(text);
            while (matcher.find()) {
                final String name = matcher.group(1);
                if (isKind(name) && !found.contains(name)) {
                    found.add(name);
                }
            }
        } catch (IOException | RuntimeException absent) {
            return new ArrayList<>();
        }
        return found;
    }

    /**
     * Whether applets may read a kind of source.
     * @param kind The kind's name.
     * @return true when it is switched on, which is where everything starts.
     */
    public static boolean enabled(final String kind) {
        return !disabled().contains(kind);
    }

    /**
     * Switches a kind on or off, for every applet.
     * @param kind The kind's name.
     * @param on Whether applets may read it.
     */
    public static void setEnabled(final String kind, final boolean on) {
        if (!isKind(kind)) {
            throw new IllegalArgumentException("no such kind of source: " + kind);
        }
        final List<String> off = disabled();
        off.remove(kind);
        if (!on) {
            off.add(kind);
        }
        try {
            Files.createDirectories(configFile().getParent());
            final StringBuilder text = new StringBuilder("{\n  \"disabled\": [");
            for (int index = 0; index < off.size(); index++) {
                text.append(index == 0 ? "" : ", ").append('"').append(off.get(index)).append('"');
            }
            text.append("]\n}\n");
            Files.writeString(configFile(), text.toString());
        } catch (IOException error) {
            throw new IllegalStateException("could not write " + configFile(), error);
        }
    }

    /**
     * Whether a name is one of the kinds.
     * @param name The name to check.
     * @return true when it is.
     */
    public static boolean isKind(final String name) {
        for (final Kind kind : KINDS) {
            if (kind.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
