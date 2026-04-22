package io.karatelabs.js.test262;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed YAML frontmatter of a test262 test file. Only the fields the runner
 * actually consults are kept. See the test262 CONTRIBUTING.md for the full schema.
 */
public record Test262Metadata(
        List<String> flags,
        List<String> features,
        List<String> includes,
        Negative negative,
        boolean raw,
        String description) {

    /** If non-null, the test is expected to fail at the given phase with the given constructor. */
    public record Negative(String phase, String type) {}

    /**
     * Parses the {@code /*--- ... ---*\/} block at the top of a test262 file.
     * Returns a metadata with empty lists and null negative if no frontmatter present.
     * <p>
     * This is a tolerant line-based reader, not a full YAML parser. test262 uses
     * a constrained subset (inline arrays for flags/features/includes, a nested
     * "negative:" block). We cover exactly that subset.
     */
    public static Test262Metadata parse(String source) {
        int start = source.indexOf("/*---");
        if (start < 0) return empty();
        int end = source.indexOf("---*/", start);
        if (end < 0) return empty();
        String body = source.substring(start + 5, end);

        List<String> flags = new ArrayList<>();
        List<String> features = new ArrayList<>();
        List<String> includes = new ArrayList<>();
        String negPhase = null;
        String negType = null;
        boolean inNegative = false;
        boolean raw = false;
        String description = null;

        for (String line : body.split("\n")) {
            String stripped = line.strip();
            if (stripped.isEmpty()) continue;

            // Top-level key starts at column 0 of the original line (after the "---" opener).
            // We use a simple heuristic: a line whose first non-space char is a letter, that
            // also contains ":" before whitespace, is a key. Nested keys are indented.
            int indent = line.length() - line.stripLeading().length();

            if (inNegative) {
                if (indent == 0) {
                    inNegative = false;
                    // fall through to top-level handling
                } else {
                    if (stripped.startsWith("phase:")) {
                        negPhase = after(stripped, "phase:").strip();
                    } else if (stripped.startsWith("type:")) {
                        negType = after(stripped, "type:").strip();
                    }
                    continue;
                }
            }

            if (stripped.startsWith("flags:")) {
                flags.addAll(parseInlineArray(after(stripped, "flags:")));
                if (flags.contains("raw")) raw = true;
            } else if (stripped.startsWith("features:")) {
                features.addAll(parseInlineArray(after(stripped, "features:")));
            } else if (stripped.startsWith("includes:")) {
                includes.addAll(parseInlineArray(after(stripped, "includes:")));
            } else if (stripped.startsWith("negative:")) {
                inNegative = true;
            } else if (stripped.startsWith("description:")) {
                description = after(stripped, "description:").strip();
            }
        }

        return new Test262Metadata(
                Collections.unmodifiableList(flags),
                Collections.unmodifiableList(features),
                Collections.unmodifiableList(includes),
                (negPhase == null && negType == null) ? null : new Negative(negPhase, negType),
                raw,
                description);
    }

    public static Test262Metadata empty() {
        return new Test262Metadata(List.of(), List.of(), List.of(), null, false, null);
    }

    private static String after(String line, String key) {
        return line.substring(line.indexOf(key) + key.length());
    }

    /** Parses an inline YAML array like {@code "[foo, bar baz, qux]"} or {@code "[]"}. */
    static List<String> parseInlineArray(String s) {
        String t = s.strip();
        if (t.isEmpty()) return List.of();
        if (t.startsWith("[")) {
            int close = t.lastIndexOf(']');
            if (close < 0) return List.of();
            String inner = t.substring(1, close);
            if (inner.isBlank()) return List.of();
            List<String> out = new ArrayList<>();
            for (String part : inner.split(",")) {
                String p = part.strip();
                if (!p.isEmpty()) out.add(p);
            }
            return out;
        }
        // Not an inline array — could be a block-style list (rare in test262 for these keys).
        return List.of();
    }
}
