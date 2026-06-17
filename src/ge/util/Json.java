package ge.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser.
 *
 * <p>The Grand Exchange APIs we consume return plain JSON, and this project has
 * no build system that can pull in a JSON library, so we parse by hand. The
 * parser is a straightforward recursive-descent reader that produces a tree of
 * the following Java types:
 * <ul>
 *   <li>{@link Map}&lt;String,Object&gt; for objects (insertion ordered)</li>
 *   <li>{@link List}&lt;Object&gt; for arrays</li>
 *   <li>{@link String} for strings</li>
 *   <li>{@link Double} for numbers</li>
 *   <li>{@link Boolean} for true/false</li>
 *   <li>{@code null} for null</li>
 * </ul>
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Parse a JSON document into a tree of Maps/Lists/primitives. */
    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.i != p.s.length()) {
            throw new IllegalArgumentException("Trailing characters at index " + p.i);
        }
        return value;
    }

    // --- Convenience typed accessors (keep call sites readable) ---------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return (List<Object>) o;
    }

    /** Read a numeric field as double, returning {@code def} when absent/null. */
    public static double num(Map<String, Object> o, String key, double def) {
        Object v = o.get(key);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    public static long lng(Map<String, Object> o, String key, long def) {
        Object v = o.get(key);
        return (v instanceof Number n) ? n.longValue() : def;
    }

    public static String str(Map<String, Object> o, String key, String def) {
        Object v = o.get(key);
        return (v instanceof String str) ? str : def;
    }

    public static boolean bool(Map<String, Object> o, String key, boolean def) {
        Object v = o.get(key);
        return (v instanceof Boolean b) ? b : def;
    }

    // --- Recursive-descent core ----------------------------------------------

    private Object readValue() {
        char c = peek();
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            i++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw err("',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw err("',' or ']'");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw err("valid escape");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double readNumber() {
        int start = i;
        if (peek() == '-') i++;
        while (i < s.length() && isNumberChar(s.charAt(i))) i++;
        return Double.parseDouble(s.substring(start, i));
    }

    private Boolean readBoolean() {
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        throw err("boolean");
    }

    private Object readNull() {
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        throw err("null");
    }

    // --- Lexing helpers ------------------------------------------------------

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private void skipWhitespace() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }

    private char peek() {
        if (i >= s.length()) throw err("more input");
        return s.charAt(i);
    }

    private char next() {
        if (i >= s.length()) throw err("more input");
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) throw err("'" + c + "'");
    }

    private IllegalArgumentException err(String expected) {
        return new IllegalArgumentException("JSON parse error at index " + i + ": expected " + expected);
    }
}
