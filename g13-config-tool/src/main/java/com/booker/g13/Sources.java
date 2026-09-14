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
        new Kind("http", "web addresses, e.g. http:weather/now#temp; the address and any token "
                + "are named in endpoints.json, never in the applet"),
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

    /** One named web address, and the credential that goes with it. */
    public static final class Endpoint {
        /** The base address, e.g. http://homeassistant.local:8123. */
        public String url = "";
        /** The bearer token, if any. Kept in this file so an applet never has to carry it. */
        public String token = "";
        /** Seconds to wait, or 0 for the daemon's own default. */
        public double timeout = 0;
        /** Whether to accept a certificate that does not check out, for a local server. */
        public boolean insecure = false;
        /** Anything else the file held - headers, say - kept so editing here does not lose it. */
        public final java.util.Map<String, Object> rest = new java.util.LinkedHashMap<>();
    }

    /** The file the daemon reads the named web addresses from. */
    public static Path endpointsFile() {
        return Visuals.configDir().resolve("endpoints.json");
    }

    /** One value out of a map whose type is not known, as text. */
    private static String text(final java.util.Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Whether a name can be an endpoint's key.
     * @param name The name to check.
     * @return true when it is letters, digits, hyphens and underscores.
     */
    public static boolean validEndpointName(final String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,40}");
    }

    /**
     * The named web addresses, in the order the file lists them.
     * @return name to endpoint.
     */
    public static java.util.Map<String, Endpoint> endpoints() {
        final java.util.Map<String, Endpoint> found = new java.util.LinkedHashMap<>();
        try {
            final Object parsed = Json.parse(Files.readString(endpointsFile()));
            if (!(parsed instanceof java.util.Map)) {
                return found;
            }
            for (final java.util.Map.Entry<?, ?> entry :
                    ((java.util.Map<?, ?>) parsed).entrySet()) {
                if (!(entry.getValue() instanceof java.util.Map)) {
                    continue;
                }
                final java.util.Map<?, ?> raw = (java.util.Map<?, ?>) entry.getValue();
                final Endpoint endpoint = new Endpoint();
                endpoint.url = text(raw, "url");
                endpoint.token = text(raw, "token");
                endpoint.timeout = raw.get("timeout") instanceof Number
                        ? ((Number) raw.get("timeout")).doubleValue() : 0;
                endpoint.insecure = Boolean.TRUE.equals(raw.get("insecure"));
                for (final java.util.Map.Entry<?, ?> pair : raw.entrySet()) {
                    final String key = String.valueOf(pair.getKey());
                    if (!key.equals("url") && !key.equals("token") && !key.equals("timeout")
                            && !key.equals("insecure")) {
                        endpoint.rest.put(key, pair.getValue());
                    }
                }
                found.put(String.valueOf(entry.getKey()), endpoint);
            }
        } catch (IOException | RuntimeException absentOrUnreadable) {
            return new java.util.LinkedHashMap<>();
        }
        return found;
    }

    /**
     * Writes the named web addresses, and nothing else can read the file.
     * @param endpoints Name to endpoint, in the order to write them.
     */
    public static void saveEndpoints(final java.util.Map<String, Endpoint> endpoints) {
        final java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (final java.util.Map.Entry<String, Endpoint> entry : endpoints.entrySet()) {
            if (!validEndpointName(entry.getKey())) {
                throw new IllegalArgumentException("an endpoint name cannot be: " + entry.getKey());
            }
            final Endpoint endpoint = entry.getValue();
            final java.util.Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("url", endpoint.url);
            if (!endpoint.token.isEmpty()) {
                one.put("token", endpoint.token);
            }
            if (endpoint.timeout > 0) {
                one.put("timeout", endpoint.timeout);
            }
            if (endpoint.insecure) {
                one.put("insecure", true);
            }
            one.putAll(endpoint.rest);
            out.put(entry.getKey(), one);
        }
        try {
            Files.createDirectories(endpointsFile().getParent());
            Files.writeString(endpointsFile(), Json.write(out));
            // A token lives in here: keep it to its owner, and say so rather than hoping.
            try {
                Files.setPosixFilePermissions(endpointsFile(),
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException notPosix) {
                // A filesystem without permissions: the file is still written, and the window says
                // which way it went.
            }
        } catch (IOException error) {
            throw new IllegalStateException("could not write " + endpointsFile(), error);
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
