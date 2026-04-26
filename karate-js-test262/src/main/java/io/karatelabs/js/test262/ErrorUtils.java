package io.karatelabs.js.test262;

import io.karatelabs.js.EngineException;

/**
 * Classifies a thrown exception into a test262-style error type name.
 * <p>
 * Prefers the structured {@link EngineException#getJsErrorName()} surface
 * (set when an uncaught JS {@code throw} propagates out of the engine). Falls
 * back to message-prefix scanning for engine-origin errors that use the
 * {@code "TypeError: ..."} / {@code "RangeError: ..."} convention (see
 * {@code JavaUtils.java}, {@code Prototype.java}, {@code JsNumberPrototype.java}).
 * <p>
 * Returns one of:
 * {@code "SyntaxError" | "TypeError" | "ReferenceError" | "RangeError" | "URIError" | "EvalError" | "Error"}
 * — or {@code null} if nothing recognizable is found.
 */
public final class ErrorUtils {

    private ErrorUtils() {}

    private static final String[] KNOWN_NAMES = {
            "SyntaxError", "TypeError", "ReferenceError",
            "RangeError", "URIError", "EvalError", "Error"
    };

    /** @return canonical error type name, or {@code null} if unrecognized. */
    public static String classify(Throwable t) {
        // Walk the cause chain for any EngineException carrying a structured JS error name.
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof EngineException ee && ee.getJsErrorName() != null) {
                return canonicalize(ee.getJsErrorName());
            }
            Throwable next = cur.getCause();
            if (next == cur) break;
            cur = next;
        }
        // Fallback: message-prefix scan (catches engine-origin "TypeError: ..." etc.).
        cur = t;
        while (cur != null) {
            String name = classifyByMessagePrefix(cur.getMessage());
            if (name != null) return name;
            Throwable next = cur.getCause();
            if (next == cur) break;
            cur = next;
        }
        return null;
    }

    /** Scans {@code text} for a recognizable {@code "<Name>: "} prefix. */
    private static String classifyByMessagePrefix(String text) {
        if (text == null) return null;
        // After unwrapping any engine "js failed: ... Error: <body>" framing, look at the body.
        String body = unwrapFraming(text);
        for (String name : KNOWN_NAMES) {
            if (body.startsWith(name + ":") || body.startsWith(name + " ")) {
                return name;
            }
        }
        // Fallback: scan for "<Name>:" appearing anywhere after a non-word boundary.
        // Catches wrapper messages like "expression: $262.createRealm().global - TypeError: ...".
        String inner = findEmbeddedErrorName(body);
        if (inner != null) return inner;
        // Heuristic for engine-emitted ReferenceError messages ("foo is not defined").
        if (body.contains("is not defined")) return "ReferenceError";
        return null;
    }

    /**
     * Scans {@code body} for an embedded {@code <Name>:} preceded by a non-word character
     * (space, dash, colon, paren, etc.), so an inner error type shows through wrapper framing
     * like {@code "expression: foo - TypeError: ..."}. Returns the first match, or {@code null}.
     */
    private static String findEmbeddedErrorName(String body) {
        for (String name : KNOWN_NAMES) {
            int from = 0;
            while (true) {
                int idx = body.indexOf(name + ":", from);
                if (idx < 0) break;
                if (idx > 0) {
                    char prev = body.charAt(idx - 1);
                    if (!Character.isLetterOrDigit(prev) && prev != '_') return name;
                }
                from = idx + 1;
            }
        }
        return null;
    }

    /** Accepts variants like "typeerror" and returns canonical form, else input unchanged. */
    private static String canonicalize(String name) {
        for (String k : KNOWN_NAMES) {
            if (k.equalsIgnoreCase(name)) return k;
        }
        return name;
    }

    /**
     * Extracts the first informative line of a message, trimmed to {@code maxLen}.
     * Strips the {@code "js failed: / ==========" } framing so JSONL messages show
     * the actual error body.
     */
    public static String firstLine(String message, int maxLen) {
        return firstLine(message, null, maxLen);
    }

    /**
     * As {@link #firstLine(String, int)} but additionally strips a leading
     * {@code <type>: } prefix when the body starts with one — avoids
     * doubling the type when the runner's display format is
     * {@code "<error_type>: <message>"} and the engine has already injected
     * the type as a prefix on the message at {@code Interpreter.evalProgram}.
     */
    public static String firstLine(String message, String type, int maxLen) {
        if (message == null) return null;
        String body = unwrapFraming(message);
        int nl = body.indexOf('\n');
        String line = nl < 0 ? body : body.substring(0, nl);
        line = line.strip();
        if (type != null) {
            String prefix = type + ":";
            if (line.startsWith(prefix)) {
                line = line.substring(prefix.length()).stripLeading();
            }
        }
        if (line.length() > maxLen) line = line.substring(0, maxLen - 1) + "…";
        return line;
    }

    /**
     * Throwable-aware variant — uses {@link EngineException#getJsMessage()} when
     * available (no parsing of host-side framing), otherwise falls back to the
     * string-based path. Prefer this in new call sites; the string-only forms
     * remain for callers that only have a {@code String} in hand.
     */
    public static String firstLine(Throwable t, String type, int maxLen) {
        if (t instanceof EngineException ee && ee.getJsMessage() != null) {
            return trimMessage(ee.getJsMessage(), type, maxLen);
        }
        return firstLine(t == null ? null : t.getMessage(), type, maxLen);
    }

    private static String trimMessage(String body, String type, int maxLen) {
        if (body == null) return null;
        int nl = body.indexOf('\n');
        String line = nl < 0 ? body : body.substring(0, nl);
        line = line.strip();
        if (type != null) {
            String prefix = type + ":";
            if (line.startsWith(prefix)) {
                line = line.substring(prefix.length()).stripLeading();
            }
        }
        if (line.length() > maxLen) line = line.substring(0, maxLen - 1) + "…";
        return line;
    }

    /**
     * Strips karate-js's standard {@code "js failed: / ========== / ... / Error: <body>"}
     * framing to return just the error body. Unchanged if no framing is detected.
     */
    private static String unwrapFraming(String message) {
        int errIdx = message.indexOf("  Error:");
        if (errIdx < 0) return message;
        String after = message.substring(errIdx + "  Error:".length()).stripLeading();
        int closer = after.indexOf("\n==========");
        if (closer >= 0) after = after.substring(0, closer);
        return after;
    }
}
