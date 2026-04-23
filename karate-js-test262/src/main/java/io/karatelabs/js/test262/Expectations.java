package io.karatelabs.js.test262;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads {@code etc/expectations.yaml} and matches a {@link Test262Case}
 * against its skip rules.
 * <p>
 * Match order: paths -> flags -> features -> includes. First match wins and
 * produces the SKIP reason. See the YAML file itself for the full schema.
 * <p>
 * This is a tiny custom parser scoped to the fixed schema this module uses.
 * We avoid the SnakeYAML dependency.
 */
public final class Expectations {

    private final List<SkipRule> paths;
    private final Map<String, String> flags;     // flag -> reason
    private final Map<String, String> features;  // feature -> reason
    private final Map<String, String> includes;  // include -> reason

    private Expectations(List<SkipRule> paths, Map<String, String> flags,
                         Map<String, String> features, Map<String, String> includes) {
        this.paths = paths;
        this.flags = flags;
        this.features = features;
        this.includes = includes;
    }

    public static Expectations load(Path yamlFile) {
        try {
            String text = Files.readString(yamlFile);
            return parse(text);
        } catch (Exception e) {
            throw new RuntimeException("failed to read expectations: " + yamlFile, e);
        }
    }

    /**
     * @return the SKIP reason if the case should be skipped, or {@code null} to run it.
     */
    public String matchSkip(Test262Case c) {
        String rel = c.relativePath();
        for (SkipRule p : paths) {
            if (p.matches(rel)) return p.reason;
        }
        Test262Metadata m = c.metadata();
        for (String f : m.flags()) {
            String reason = flags.get(f);
            if (reason != null) return reason;
        }
        for (String f : m.features()) {
            String reason = features.get(f);
            if (reason != null) return reason;
        }
        for (String inc : m.includes()) {
            String reason = includes.get(inc);
            if (reason != null) return reason;
        }
        return null;
    }

    /* -------------------------------- parsing -------------------------------- */

    private static Expectations parse(String yaml) {
        List<SkipRule> paths = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        Map<String, String> features = new LinkedHashMap<>();
        Map<String, String> includes = new LinkedHashMap<>();

        // State machine for a very restricted YAML subset. We parse only what the
        // bundled expectations.yaml uses: a top-level "skip:" block containing
        // 4 nested lists (paths, flags, features, includes), each list item a
        // 2-field object (key + reason).
        String[] lines = yaml.split("\n", -1);
        String currentSection = null;          // "paths" | "flags" | "features" | "includes"
        String pendingKey = null;
        String pendingReason = null;
        String pendingField = null;            // field name that "key" resolves to (path/flag/feature/include)
        int i = 0;
        while (i < lines.length) {
            String raw = stripComment(lines[i]);
            i++;
            String stripped = raw.strip();
            if (stripped.isEmpty()) continue;

            int indent = raw.length() - raw.stripLeading().length();

            if (indent == 0) {
                if (stripped.endsWith(":")) {
                    // could be "version:" or "skip:" — ignore top-level scalars we don't use
                }
                continue;
            }
            if (indent == 2 && stripped.endsWith(":")) {
                String section = stripped.substring(0, stripped.length() - 1);
                switch (section) {
                    case "paths":    currentSection = "paths";    pendingField = "path";    break;
                    case "flags":    currentSection = "flags";    pendingField = "flag";    break;
                    case "features": currentSection = "features"; pendingField = "feature"; break;
                    case "includes": currentSection = "includes"; pendingField = "include"; break;
                    default:         currentSection = null;       pendingField = null;      break;
                }
                continue;
            }
            if (currentSection == null) continue;

            // List items at indent 4 start with "- key: value"; continuation at indent 6 carries "reason: ..."
            if (stripped.startsWith("- ")) {
                flush(currentSection, pendingKey, pendingReason, paths, flags, features, includes);
                pendingKey = null;
                pendingReason = null;
                String rest = stripped.substring(2);
                // rest like "path: test/foo/**"
                int colon = rest.indexOf(':');
                if (colon < 0) continue;
                String k = rest.substring(0, colon).strip();
                String v = unquote(rest.substring(colon + 1).strip());
                if (k.equals(pendingField)) {
                    pendingKey = v;
                }
            } else {
                int colon = stripped.indexOf(':');
                if (colon < 0) continue;
                String k = stripped.substring(0, colon).strip();
                String v = unquote(stripped.substring(colon + 1).strip());
                if (k.equals("reason")) {
                    pendingReason = v;
                } else if (k.equals(pendingField)) {
                    // list item with mixed order (reason first, key second)
                    pendingKey = v;
                }
            }
        }
        flush(currentSection, pendingKey, pendingReason, paths, flags, features, includes);

        return new Expectations(paths, flags, features, includes);
    }

    private static void flush(String section, String key, String reason,
                              List<SkipRule> paths,
                              Map<String, String> flags,
                              Map<String, String> features,
                              Map<String, String> includes) {
        if (section == null || key == null) return;
        String r = reason == null ? "(no reason)" : reason;
        switch (section) {
            case "paths"    -> paths.add(new SkipRule(key, r));
            case "flags"    -> flags.put(key, r);
            case "features" -> features.put(key, r);
            case "includes" -> includes.put(key, r);
            default -> { /* ignore */ }
        }
    }

    private static String stripComment(String s) {
        // simple: "#" outside a quoted string begins a comment
        int hash = s.indexOf('#');
        if (hash < 0) return s;
        // Only strip if the # is not part of a quoted value.
        // For our constrained schema (no quoted values with #), plain split is fine.
        return s.substring(0, hash);
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /* -------------------------- path glob matching -------------------------- */

    /** A path skip rule, compiled from a glob pattern like {@code test/intl402/**}. */
    private record SkipRule(String pattern, String reason) {
        private static final Map<String, Pattern> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

        boolean matches(String path) {
            Pattern p = CACHE.computeIfAbsent(pattern, SkipRule::globToRegex);
            return p.matcher(path).matches();
        }

        private static Pattern globToRegex(String glob) {
            StringBuilder sb = new StringBuilder("^");
            int i = 0;
            while (i < glob.length()) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i += 2;
                        if (i < glob.length() && glob.charAt(i) == '/') i++;
                        continue;
                    }
                    sb.append("[^/]*");
                } else if (c == '?') {
                    sb.append('.');
                } else if ("\\.^$+()[]{}|".indexOf(c) >= 0) {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
                i++;
            }
            sb.append('$');
            return Pattern.compile(sb.toString());
        }
    }
}
