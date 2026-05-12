package io.karatelabs.js;

import io.karatelabs.common.StringUtils;
import io.karatelabs.parser.Node;
import io.karatelabs.parser.NodeType;
import io.karatelabs.parser.TokenType;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class NodeUtils {

    static final Logger logger = LoggerFactory.getLogger(NodeUtils.class);

    /**
     * Lenient JSON-ish parser for test expectations. Mirrors json-smart's
     * {@code JSONValue.parse} accept surface so legacy NodeUtils.match calls
     * keep working:
     * <ul>
     *   <li>strict JSON (RFC 8259) shapes
     *   <li>single-quoted strings
     *   <li>unquoted identifier keys (incl. {@code $foo})
     *   <li>top-level bare tokens treated as String literals
     *   <li>array/object elements that aren't a JSON shape treated as
     *       trimmed string values (everything up to the next {@code ,]}/{@code }})
     * </ul>
     */
    public static <T> T fromJson(String s) {
        if (s == null) return null;
        return (T) new LenientJsonParser(s).parseTop();
    }

    public static String toJson(Object o) {
        return StringUtils.formatJson(o, false, false, false);
    }

    private static final class LenientJsonParser {
        private final String s;
        private final int len;
        private int p;

        LenientJsonParser(String s) {
            this.s = s;
            this.len = s.length();
        }

        Object parseTop() {
            skipWs();
            if (p >= len) return "";
            char c = s.charAt(p);
            if (c == '[' || c == '{' || c == '"' || c == '\'') {
                return value();
            }
            // Bare top-level token. json-smart parses single tokens that look
            // numeric / true / false / null as their typed values; everything
            // else (including comma-bearing input like "1,2,3") is the raw
            // input as a String.
            return interpretBare(s);
        }

        // value when nested inside an array/object — supports unquoted bare tokens.
        // Stops at the boundary char in `terminators` (e.g. ",]" or ",}").
        private Object valueOrBare(String terminators) {
            skipWs();
            if (p >= len) throw new RuntimeException("unexpected end at " + p + " in: " + s);
            char c = s.charAt(p);
            if (c == '{' || c == '[' || c == '"' || c == '\'') {
                return value();
            }
            // bare token: read until terminator, treat trim-result as string
            // unless it parses as a number / true / false / null.
            int start = p;
            int depth = 0;
            while (p < len) {
                char ch = s.charAt(p);
                if (depth == 0 && terminators.indexOf(ch) >= 0) break;
                if (ch == '[' || ch == '{') depth++;
                else if (ch == ']' || ch == '}') depth--;
                p++;
            }
            String tok = s.substring(start, p).trim();
            return interpretBare(tok);
        }

        private Object value() {
            char c = s.charAt(p);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"' || c == '\'') return str(c);
            // shouldn't reach here from parseTop / valueOrBare
            throw new RuntimeException("unexpected '" + c + "' at " + p + " in: " + s);
        }

        private static Object interpretBare(String tok) {
            if (tok.isEmpty()) return tok;
            switch (tok) {
                case "true": return Boolean.TRUE;
                case "false": return Boolean.FALSE;
                case "null": return null;
                default:
                    if (looksNumeric(tok)) {
                        try {
                            if (tok.contains(".") || tok.contains("e") || tok.contains("E")) {
                                return Double.parseDouble(tok);
                            }
                            long v = Long.parseLong(tok);
                            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return (int) v;
                            return v;
                        } catch (NumberFormatException nfe) {
                            // fall through — treat as string
                        }
                    }
                    return tok;
            }
        }

        private static boolean looksNumeric(String tok) {
            int i = 0, n = tok.length();
            if (i < n && tok.charAt(i) == '-') i++;
            if (i >= n) return false;
            // require at least one digit before any other numeric char
            if (tok.charAt(i) < '0' || tok.charAt(i) > '9') return false;
            for (; i < n; i++) {
                char c = tok.charAt(i);
                if ((c < '0' || c > '9') && c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') {
                    return false;
                }
            }
            return true;
        }

        private Map<String, Object> obj() {
            p++;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            skipWs();
            if (p < len && s.charAt(p) == '}') { p++; return m; }
            while (true) {
                skipWs();
                String key = keyName();
                skipWs();
                if (p >= len || s.charAt(p) != ':') throw new RuntimeException("expected ':' at " + p + " in: " + s);
                p++;
                m.put(key, valueOrBare(",}"));
                skipWs();
                if (p >= len) throw new RuntimeException("unterminated object at " + p + " in: " + s);
                char c = s.charAt(p);
                if (c == ',') { p++; continue; }
                if (c == '}') { p++; return m; }
                throw new RuntimeException("expected ',' or '}' at " + p + " in: " + s);
            }
        }

        private List<Object> arr() {
            p++;
            List<Object> list = new ArrayList<>();
            skipWs();
            if (p < len && s.charAt(p) == ']') { p++; return list; }
            while (true) {
                list.add(valueOrBare(",]"));
                skipWs();
                if (p >= len) throw new RuntimeException("unterminated array at " + p + " in: " + s);
                char c = s.charAt(p);
                if (c == ']') { p++; return list; }
                // matches json-smart's permissive accept: skip an optional ',' and
                // keep reading elements. Two adjacent quoted/array values without
                // a comma become two list elements.
                if (c == ',') p++;
            }
        }

        private String keyName() {
            if (p >= len) throw new RuntimeException("expected key at " + p);
            char c = s.charAt(p);
            if (c == '"' || c == '\'') return str(c);
            // unquoted: read identifier-ish (letters/digits/_/$/.)
            int start = p;
            while (p < len) {
                char ch = s.charAt(p);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '-' || ch == '.') p++;
                else break;
            }
            if (start == p) throw new RuntimeException("expected key at " + p + " in: " + s);
            return s.substring(start, p);
        }

        private String str(char q) {
            p++;
            StringBuilder sb = new StringBuilder();
            while (p < len) {
                char c = s.charAt(p);
                if (c == q) { p++; return sb.toString(); }
                if (c == '\\') {
                    p++;
                    if (p >= len) throw new RuntimeException("dangling escape");
                    char e = s.charAt(p++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\'': sb.append('\''); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u': {
                            if (p + 4 > len) throw new RuntimeException("bad \\u escape");
                            sb.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                            p += 4;
                            break;
                        }
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                    p++;
                }
            }
            throw new RuntimeException("unterminated string");
        }

        private void skipWs() {
            while (p < len) {
                char c = s.charAt(p);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') p++;
                else return;
            }
        }
    }

    public static void match(Object actual, String json) {
        Object expected = fromJson(json);
        isEqualTo(actual, expected);
    }

    public static boolean isEqualTo(Object actual, Object expected) {
        try {
            if (expected instanceof List) {
                if (actual instanceof List) {
                    List<Object> actList = (List<Object>) actual;
                    List<Object> expList = (List<Object>) expected;
                    if (actList.size() != expList.size()) {
                        Assertions.fail("List size mismatch: " + actList.size() + " - " + expList.size());
                    }
                    int count = expList.size();
                    // Use getElement on JsArray so Terms.UNDEFINED is preserved —
                    // the List.get override unwraps to Java null for Java-interop
                    // ergonomics, but harness comparisons want raw JS values.
                    for (int i = 0; i < count; i++) {
                        Object actItem = actual instanceof JsArray ja ? ja.getElement(i) : actList.get(i);
                        Object expItem = expList.get(i);
                        boolean result = isEqualTo(actItem, expItem);
                        if (!result) {
                            Assertions.fail("List item mismatch at index " + i + ": " + actList.size() + " - " + expList.size());
                        }
                    }
                    return true;
                } else {
                    Assertions.fail("List expected, actual is: " + actual);
                }
            } else if (expected instanceof Map) {
                if (actual instanceof ObjectLike) {
                    actual = ((ObjectLike) actual).toMap();
                }
                if (actual instanceof Map) {
                    Map<String, Object> actMap = (Map<String, Object>) actual;
                    Map<String, Object> expMap = (Map<String, Object>) expected;
                    if (actMap.size() != expMap.size()) {
                        Assertions.fail("Map size mismatch: " + actMap.size() + " - " + expMap.size());
                    }
                    for (String key : expMap.keySet()) {
                        if (!actMap.containsKey(key)) {
                            Assertions.fail("Map does not contain key: " + key);
                        }
                        Object actValue = actMap.get(key);
                        Object expValue = expMap.get(key);
                        boolean result = isEqualTo(actValue, expValue);
                        if (!result) {
                            Assertions.fail("Map value mismatch for key " + key + ": " + actValue + " - " + expValue);
                        }
                    }
                    return true;
                } else {
                    Assertions.fail("Map expected, actual is: " + actual);
                }
            } else { // string or primitive
                if ("undefined".equals(expected)) {
                    expected = Terms.UNDEFINED;
                }
                Assertions.assertEquals(expected, actual);
            }
        } catch (Throwable t) {
            logger.error("assertion failed:\nexpected:{}\nactual=>:{}", toJson(expected), toJson(actual));
            throw t;
        }
        return true;
    }

    public static Object ser(Node node) {
        switch (node.type) {
            case PAREN_EXPR:
                return ser(node.get(1));
            case OBJECT_ELEM:
                if (node.size() < 3) { // es6 enhanced object literals
                    return ser(node.getFirst());
                } else {
                    return List.of(ser(node.get(0)) + ":", ser(node.get(2)));
                }
            case ARRAY_ELEM:
                return ser(node.get(0));
            case PROGRAM:
            case ROOT:
                // include key in serialized output
                String key = node.type.name();
                if (node.size() == 1) {
                    return Collections.singletonMap(key, ser(node.get(0)));
                } else {
                    List<Object> list = new ArrayList<>(node.size());
                    for (int i = 0, n = node.size(); i < n; i++) {
                        list.add(ser(node.get(i)));
                    }
                    return Collections.singletonMap(key, list);
                }
            case TOKEN:
                switch (node.token.type) {
                    case EOF:
                        return "EOF";
                    case IDENT:
                        return "$" + node.token.getText();
                    case S_STRING:
                    case D_STRING:
                    case NUMBER:
                    case NULL:
                    case TRUE:
                    case FALSE:
                        return Terms.literalValue(node.token);
                    default:
                        return node.token.getText();
                }
            default:
                if (node.size() == 1) {
                    return ser(node.get(0));
                } else {
                    List<Object> list = new ArrayList<>();
                    for (int i = 0, n = node.size(); i < n; i++) {
                        list.add(ser(node.get(i)));
                    }
                    return list;
                }
        }
    }

    public static void assertEquals(String text, Node node, String string) {
        Object expected = fromJson(string);
        Object actual = ser(node);
        String expectedJson = toJson(expected);
        String actualJson = toJson(actual);
        try {
            Assertions.assertEquals(expectedJson, actualJson);
        } catch (Throwable t) {
            // logger.debug("tree:\n{}", toString(node));
            logger.debug("text:\n{}", text);
            throw t;
        }
    }

    private static void spaces(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append(' ');
        }
    }

    private static void recurse(StringBuilder sb, int level, Node node) {
        spaces(sb, level);
        sb.append(node).append('\n');
        for (int i = 0, n = node.size(); i < n; i++) {
            recurse(sb, level + 1, node.get(i));
        }
    }

    private static String toString(Node node) {
        StringBuilder sb = new StringBuilder();
        recurse(sb, 0, node);
        return sb.toString();
    }

}
