package net.cloudclient.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free JSON reader/writer.
 *
 * <p>The client parses untrusted input (downloaded {@code .cloudpack}
 * manifests), so this parser is strict: it rejects trailing content, control
 * characters in strings, leading zeroes, and {@code NaN}/{@code Infinity}. It
 * never recurses without bound (see {@link #MAX_DEPTH}).
 *
 * <p>Values map to: {@link Map} (object), {@link List} (array), {@link String},
 * {@link Double} (number), {@link Boolean}, and {@code null}.
 */
public final class Json {

    /** Deep enough for any sane manifest, shallow enough to defeat bombs. */
    static final int MAX_DEPTH = 64;

    private Json() {
    }

    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- parsing

    public static Object parse(String text) {
        if (text == null) throw new JsonException("null input");
        Parser p = new Parser(text);
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("Trailing content at index " + p.index);
        }
        return value;
    }

    private static final class Parser {
        private final String src;
        int index;

        Parser(String src) {
            this.src = src;
        }

        boolean atEnd() {
            return index >= src.length();
        }

        void skipWhitespace() {
            while (index < src.length()) {
                char c = src.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') index++;
                else break;
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw new JsonException("Nesting too deep at index " + index);
            skipWhitespace();
            if (atEnd()) throw new JsonException("Unexpected end of input");
            char c = src.charAt(index);
            switch (c) {
                case '{':
                    return readObject(depth + 1);
                case '[':
                    return readArray(depth + 1);
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            index++; // {
            skipWhitespace();
            if (!atEnd() && src.charAt(index) == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || src.charAt(index) != '"') {
                    throw new JsonException("Expected object key at index " + index);
                }
                String key = readString();
                skipWhitespace();
                if (atEnd() || src.charAt(index) != ':') {
                    throw new JsonException("Expected ':' at index " + index);
                }
                index++;
                Object value = readValue(depth);
                map.put(key, value);
                skipWhitespace();
                if (atEnd()) throw new JsonException("Unterminated object");
                char c = src.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == '}') {
                    index++;
                    return map;
                }
                throw new JsonException("Expected ',' or '}' at index " + index);
            }
        }

        private List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<Object>();
            index++; // [
            skipWhitespace();
            if (!atEnd() && src.charAt(index) == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(readValue(depth));
                skipWhitespace();
                if (atEnd()) throw new JsonException("Unterminated array");
                char c = src.charAt(index);
                if (c == ',') {
                    index++;
                    continue;
                }
                if (c == ']') {
                    index++;
                    return list;
                }
                throw new JsonException("Expected ',' or ']' at index " + index);
            }
        }

        private String readString() {
            StringBuilder sb = new StringBuilder();
            index++; // opening quote
            while (true) {
                if (atEnd()) throw new JsonException("Unterminated string");
                char c = src.charAt(index++);
                if (c == '"') return sb.toString();
                if (c < 0x20) {
                    throw new JsonException("Control character in string at index " + (index - 1));
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) throw new JsonException("Unterminated escape");
                char e = src.charAt(index++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (index + 4 > src.length()) throw new JsonException("Bad \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(src.substring(index, index + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new JsonException("Bad \\u escape at index " + index);
                        }
                        index += 4;
                        break;
                    default:
                        throw new JsonException("Bad escape '\\" + e + "' at index " + (index - 1));
                }
            }
        }

        private Double readNumber() {
            int start = index;
            if (!atEnd() && (src.charAt(index) == '-' || src.charAt(index) == '+')) index++;
            int digits = 0;
            while (!atEnd() && Character.isDigit(src.charAt(index))) {
                index++;
                digits++;
            }
            if (digits == 0) throw new JsonException("Expected value at index " + start);
            if (!atEnd() && src.charAt(index) == '.') {
                index++;
                int frac = 0;
                while (!atEnd() && Character.isDigit(src.charAt(index))) {
                    index++;
                    frac++;
                }
                if (frac == 0) throw new JsonException("Trailing '.' at index " + start);
            }
            if (!atEnd() && (src.charAt(index) == 'e' || src.charAt(index) == 'E')) {
                index++;
                if (!atEnd() && (src.charAt(index) == '-' || src.charAt(index) == '+')) index++;
                int exp = 0;
                while (!atEnd() && Character.isDigit(src.charAt(index))) {
                    index++;
                    exp++;
                }
                if (exp == 0) throw new JsonException("Bad exponent at index " + start);
            }
            String raw = src.substring(start, index);
            // Reject forms double parses but JSON does not allow.
            if (raw.startsWith("+") || raw.startsWith("-0") && raw.length() > 2 && raw.charAt(2) != '.'
                    || raw.matches("-?0\\d+.*")) {
                throw new JsonException("Invalid number '" + raw + "' at index " + start);
            }
            try {
                double d = Double.parseDouble(raw);
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new JsonException("Non-finite number at index " + start);
                }
                return Double.valueOf(d);
            } catch (NumberFormatException ex) {
                throw new JsonException("Invalid number '" + raw + "' at index " + start);
            }
        }

        private void expect(String literal) {
            if (!src.startsWith(literal, index)) {
                throw new JsonException("Expected '" + literal + "' at index " + index);
            }
            index += literal.length();
        }
    }

    // ---------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(value, sb, -1, 0);
        return sb.toString();
    }

    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder(512);
        writeValue(value, sb, 2, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb, int indent, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            writeNumber((Number) value, sb);
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb, indent, depth);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb, indent, depth);
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeNumber(Number n, StringBuilder sb) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            sb.append(n.longValue());
            return;
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("0");
            return;
        }
        if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15D) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb, int indent, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            newline(sb, indent, depth + 1);
            writeString(e.getKey(), sb);
            sb.append(':');
            if (indent >= 0) sb.append(' ');
            writeValue(e.getValue(), sb, indent, depth + 1);
        }
        newline(sb, indent, depth);
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb, int indent, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            newline(sb, indent, depth + 1);
            writeValue(list.get(i), sb, indent, depth + 1);
        }
        newline(sb, indent, depth);
        sb.append(']');
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        if (indent < 0) return;
        sb.append('\n');
        for (int i = 0; i < indent * depth; i++) sb.append(' ');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // -------------------------------------------------------------- accessors

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) return (Map<String, Object>) value;
        throw new JsonException("Expected object, got " + typeName(value));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List) return (List<Object>) value;
        throw new JsonException("Expected array, got " + typeName(value));
    }

    public static String getString(Map<String, Object> obj, String key, String def) {
        Object v = obj.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static int getInt(Map<String, Object> obj, String key, int def) {
        Object v = obj.get(key);
        return v instanceof Number ? (int) Math.round(((Number) v).doubleValue()) : def;
    }

    public static double getDouble(Map<String, Object> obj, String key, double def) {
        Object v = obj.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public static boolean getBool(Map<String, Object> obj, String key, boolean def) {
        Object v = obj.get(key);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }

    public static Map<String, Object> child(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof Map ? asObject(v) : null;
    }

    public static List<Object> children(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof List ? asArray(v) : null;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
