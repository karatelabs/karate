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
        // Heuristic for engine-emitted ReferenceError messages ("foo is not defined").
        if (body.contains("is not defined")) return "ReferenceError";
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
        if (message == null) return null;
        String body = unwrapFraming(message);
        int nl = body.indexOf('\n');
        String line = nl < 0 ? body : body.substring(0, nl);
        line = line.strip();
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
