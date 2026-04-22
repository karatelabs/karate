package io.karatelabs.js.test262;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writing + reading for the small fixed schemas this module uses
 * (JSONL records, run-meta, embedded summary). Avoids pulling a JSON library
 * into the module.
 */
final class JsonLite {

    private JsonLite() {}

    static void writeString(StringBuilder sb, String s) {
        if (s == null) { sb.append("null"); return; }
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Serializes a Map or List of simple scalars into pretty JSON (2-space indent). */
    static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder(256);
        writePrettyInto(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void writePrettyInto(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String s) {
            writeString(sb, s);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }
        if (value instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int i = 0, last = m.size() - 1;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 1);
                writeString(sb, e.getKey().toString());
                sb.append(": ");
                writePrettyInto(sb, e.getValue(), indent + 1);
                if (i++ < last) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) { sb.append("[]"); return; }
            sb.append("[\n");
            int i = 0, last = list.size() - 1;
            for (Object o : list) {
                pad(sb, indent + 1);
                writePrettyInto(sb, o, indent + 1);
                if (i++ < last) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
            return;
        }
        writeString(sb, value.toString());
    }

    private static void pad(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }
}
