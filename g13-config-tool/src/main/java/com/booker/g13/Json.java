package com.booker.g13;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, for applet files.
 *
 * The config tool has no JSON library, and the helpers that read {@code visuals.json} are flat
 * pattern matches. An applet is nested — a list of widgets, each with its own fields — so this
 * reads a whole document into maps and lists, and writes one back with its keys in the order they
 * were put in, so a file edited here stays readable and diffable instead of becoming one line.
 */
public final class Json {

    private Json() {
    }

    /** Thrown when the text is not JSON, or is truncated. */
    public static class BadJson extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * @param message What went wrong.
         */
        public BadJson(final String message) {
            super(message);
        }
    }

    /**
     * Reads a document.
     * @param text The JSON.
     * @return maps, lists, strings, numbers, booleans or null.
     */
    public static Object parse(final String text) {
        final Reader reader = new Reader(text);
        reader.skipSpace();
        final Object value = reader.value();
        reader.skipSpace();
        if (!reader.done()) {
            throw new BadJson("trailing text after the document at " + reader.at);
        }
        return value;
    }

    /**
     * Reads an object, or makes an empty one if the text is not an object.
     * @param text The JSON.
     * @return the object.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(final String text) {
        final Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new BadJson("the document is not a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /**
     * Writes a document, three-space indented, keys in their own order.
     * @param value The value to write.
     * @return the text, ending in a newline.
     */
    public static String write(final Object value) {
        final StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.append('\n').toString();
    }

    private static void writeValue(final StringBuilder out, final Object value, final int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map) {
            writeObject(out, value, depth);
        } else if (value instanceof List) {
            writeArray(out, (List<?>) value, depth);
        } else if (value instanceof String) {
            quote(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            final double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && !Double.isInfinite(number)) {
                out.append((long) number);          // 12.0 reads better as 12 in a file people edit
            } else {
                out.append(number);
            }
        } else {
            out.append(value);
        }
    }

    private static void writeObject(final StringBuilder out, final Object value, final int depth) {
        final Map<?, ?> map = (Map<?, ?>) value;
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            quote(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue(), depth + 1);
            out.append(++index < map.size() ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeArray(final StringBuilder out, final List<?> list, final int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        final boolean simple = list.stream().allMatch(item -> item == null || item instanceof Number
                || item instanceof Boolean);
        out.append('[');
        for (int index = 0; index < list.size(); index++) {
            if (!simple) {
                out.append('\n');
                indent(out, depth + 1);
            } else if (index > 0) {
                out.append(' ');
            }
            writeValue(out, list.get(index), depth + 1);
            if (simple && index < list.size() - 1) {
                out.append(',');
            } else if (!simple && index < list.size() - 1) {
                out.append(',');
            }
        }
        if (!simple) {
            out.append('\n');
            indent(out, depth);
        }
        out.append(']');
    }

    private static void indent(final StringBuilder out, final int depth) {
        out.append("   ".repeat(Math.max(0, depth)));
    }

    private static void quote(final StringBuilder out, final String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            final char character = text.charAt(index);
            switch (character) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
            }
        }
        out.append('"');
    }

    /** A reader over the text, with a position it reports in errors. */
    private static final class Reader {
        private final String text;
        private int at;

        Reader(final String text) {
            this.text = text;
        }

        boolean done() {
            return at >= text.length();
        }

        void skipSpace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        Object value() {
            skipSpace();
            if (done()) {
                throw new BadJson("the document ends where a value was expected");
            }
            final char character = text.charAt(at);
            switch (character) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        private void expect(final String word) {
            if (!text.startsWith(word, at)) {
                throw new BadJson("expected " + word + " at " + at);
            }
            at += word.length();
        }

        private Map<String, Object> object() {
            final Map<String, Object> map = new LinkedHashMap<>();
            at++;
            skipSpace();
            if (!done() && text.charAt(at) == '}') {
                at++;
                return map;
            }
            while (true) {
                skipSpace();
                final String key = string();
                skipSpace();
                if (done() || text.charAt(at) != ':') {
                    throw new BadJson("expected ':' after the key " + key + " at " + at);
                }
                at++;
                map.put(key, value());
                skipSpace();
                if (done()) {
                    throw new BadJson("the object is not closed");
                }
                final char next = text.charAt(at++);
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    throw new BadJson("expected ',' or '}' at " + (at - 1));
                }
            }
        }

        private List<Object> array() {
            final List<Object> list = new ArrayList<>();
            at++;
            skipSpace();
            if (!done() && text.charAt(at) == ']') {
                at++;
                return list;
            }
            while (true) {
                list.add(value());
                skipSpace();
                if (done()) {
                    throw new BadJson("the array is not closed");
                }
                final char next = text.charAt(at++);
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    throw new BadJson("expected ',' or ']' at " + (at - 1));
                }
            }
        }

        private String string() {
            if (done() || text.charAt(at) != '"') {
                throw new BadJson("expected a string at " + at);
            }
            final StringBuilder out = new StringBuilder();
            at++;
            while (true) {
                if (done()) {
                    throw new BadJson("the string is not closed");
                }
                final char character = text.charAt(at++);
                if (character == '"') {
                    return out.toString();
                }
                if (character != '\\') {
                    out.append(character);
                    continue;
                }
                final char escape = text.charAt(at++);
                switch (escape) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'r': out.append('\r'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                        break;
                    default: out.append(escape);
                }
            }
        }

        private Object number() {
            final int start = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            final String text1 = text.substring(start, at);
            try {
                return Double.valueOf(text1);
            } catch (NumberFormatException notANumber) {
                throw new BadJson("expected a number at " + start + " but found '" + text1 + "'");
            }
        }
    }
}
