package io.karatelabs.js.test262;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads {@code <run-dir>/results.jsonl} + {@code <run-dir>/run-meta.json} and
 * writes a static HTML dashboard under {@code <run-dir>/html/}. Two pages:
 * <ul>
 *   <li>{@code index.html} — fast-loading: run-meta header + per-slice
 *       summary tiles + a fully-expanded directory tree on the left.
 *       Clicking a tree node navigates to {@code details.html#<slice>}.</li>
 *   <li>{@code details.html} — heavy: full per-test list (PASS / FAIL / SKIP),
 *       grouped by depth-2 slice ({@code test/built-ins/Array} etc.) with a
 *       hash anchor per slice. Search box + status filter at top.</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   java io.karatelabs.js.test262.Test262Report --run-dir &lt;run-dir&gt;
 * </pre>
 * The run-dir is the path that {@link Test262Runner} prints on completion
 * (or that {@code etc/run.sh} computes once and passes through).
 */
public final class Test262Report {

    private static final Logger logger = LoggerFactory.getLogger(Test262Report.class);

    public static void main(String[] args) throws Exception {
        Path runDir = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--run-dir" -> runDir = Paths.get(args[++i]);
                case "-h", "--help" -> { printHelp(); return; }
                default -> {
                    System.err.println("unknown arg: " + args[i]);
                    printHelp();
                    System.exit(2);
                }
            }
        }

        if (runDir == null) {
            System.err.println("--run-dir <path> is required.");
            System.err.println("(Test262Runner prints the run dir on completion; pass it here.)");
            printHelp();
            System.exit(2);
        }
        if (!Files.isDirectory(runDir)) {
            System.err.println("run dir not found: " + runDir.toAbsolutePath());
            System.exit(2);
        }
        Path results = runDir.resolve("results.jsonl");
        Path runMeta = runDir.resolve("run-meta.json");
        if (!Files.isRegularFile(results)) {
            System.err.println("results.jsonl not found in run dir: " + results.toAbsolutePath());
            System.err.println("Did the runner complete? (Look for results.jsonl.partial if it aborted.)");
            System.exit(2);
        }

        // Set BEFORE the first SLF4J call so logback's file appender writes
        // into the same run dir (and uses append=true so it doesn't blank
        // the runner's banner+summary).
        System.setProperty(Test262Runner.RUN_DIR_PROP, runDir.toString());

        List<Row> rows = readResults(results);
        String metaJson = Files.isRegularFile(runMeta) ? Files.readString(runMeta) : "{}";

        Path outDir = runDir.resolve("html");
        Files.createDirectories(outDir);
        copyResource("/report/styles.css", outDir.resolve("styles.css"));
        copyResource("/report/app.js", outDir.resolve("app.js"));

        int pass = 0, fail = 0, skip = 0;
        for (Row r : rows) {
            switch (r.status) { case "PASS" -> pass++; case "FAIL" -> fail++; case "SKIP" -> skip++; }
        }
        Counts overall = new Counts(pass, fail, skip);

        // Group rows by depth-2 slice (test/X/Y). Rows whose path doesn't
        // match (no slash at depth 2) fall under their depth-1 prefix.
        Map<String, List<Row>> bySlice = new TreeMap<>();
        for (Row r : rows) {
            String slice = sliceOf(r.path);
            bySlice.computeIfAbsent(slice, k -> new ArrayList<>()).add(r);
        }

        // Build a directory tree for the index sidebar.
        DirNode root = new DirNode("test");
        for (Row r : rows) {
            String[] parts = r.path.split("/");
            if (parts.length >= 2 && "test".equals(parts[0])) addToTree(root, parts, 1, r);
        }

        writeIndex(outDir.resolve("index.html"), overall, bySlice, root, metaJson);
        writeDetails(outDir.resolve("details.html"), overall, bySlice, metaJson);

        logger.info("report written  rows={} pass={} fail={} skip={} out={}",
                rows.size(), pass, fail, skip, outDir.toAbsolutePath());
        System.out.println("Wrote report to: " + outDir.toAbsolutePath());
        System.out.println("  index:   " + outDir.toAbsolutePath().resolve("index.html"));
        System.out.println("  details: " + outDir.toAbsolutePath().resolve("details.html"));
    }

    private static void printHelp() {
        System.out.println("""
                Usage: Test262Report --run-dir <path>

                  --run-dir <path>   The per-run directory written by Test262Runner.
                                     Contains results.jsonl and run-meta.json.
                                     Test262Runner prints this path on completion.

                Writes a two-page HTML report under <run-dir>/html/:
                  index.html    — tree + summary tiles
                  details.html  — full per-test list with search + filter
                """);
    }

    /* ------------------------------- model ------------------------------- */

    record Row(String path, String status, String errorType, String message, String reason) {}
    record Counts(int pass, int fail, int skip) {
        int total() { return pass + fail + skip; }
    }

    private static List<Row> readResults(Path jsonl) throws IOException {
        List<Row> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(jsonl, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String path = between(line, "\"path\":\"", "\"");
                String status = between(line, "\"status\":\"", "\"");
                String errorType = between(line, "\"error_type\":\"", "\"");
                String message = between(line, "\"message\":\"", "\"");
                String reason = between(line, "\"reason\":\"", "\"");
                if (path != null && status != null) out.add(new Row(path, status, errorType, message, reason));
            }
        }
        out.sort(Comparator.comparing(Row::path));
        return out;
    }

    /** Slice key for a test path. {@code test/built-ins/Array/prototype/map/foo.js}
     *  → {@code built-ins/Array}. Top-level files (rare) → {@code test}. */
    static String sliceOf(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3 && "test".equals(parts[0])) {
            return parts[1] + "/" + parts[2];
        }
        if (parts.length >= 2 && "test".equals(parts[0])) return parts[1];
        return parts.length > 0 ? parts[0] : "(unknown)";
    }

    /** URL-safe anchor for a slice key. {@code built-ins/Array} → {@code built-ins-Array}. */
    static String anchorOf(String slice) {
        return slice.replace('/', '-').replace('.', '-');
    }

    /* ------------------------------ index.html ------------------------------ */

    private static void writeIndex(Path file, Counts overall, Map<String, List<Row>> bySlice,
                                   DirNode root, String metaJson) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>karate-js · test262 results</title>
                <link rel="stylesheet" href="styles.css">
                </head>
                <body class="page-index">
                <header>
                  <h1>karate-js · test262</h1>
                  <div id="meta"></div>
                """);
        appendSummaryPills(sb, overall);
        sb.append("</header>\n");

        sb.append("<div class=\"layout\">\n");

        // Sidebar: tree
        sb.append("<aside class=\"tree\"><h2>Tree</h2>\n");
        renderTree(sb, root, 0, new ArrayList<>());
        sb.append("</aside>\n");

        // Main: per-slice summary tiles
        sb.append("<main class=\"tiles\"><h2>Slices</h2>\n<div class=\"tile-grid\">\n");
        for (Map.Entry<String, List<Row>> e : bySlice.entrySet()) {
            String slice = e.getKey();
            Counts c = countsOf(e.getValue());
            sb.append("<a class=\"tile\" href=\"details.html#").append(escape(anchorOf(slice))).append("\">\n");
            sb.append("  <div class=\"tile-name\">").append(escape(slice)).append("</div>\n");
            sb.append("  <div class=\"tile-counts\">")
              .append("<span class=\"pill pass\">").append(c.pass).append("</span> ")
              .append("<span class=\"pill fail\">").append(c.fail).append("</span> ")
              .append("<span class=\"pill skip\">").append(c.skip).append("</span>")
              .append("</div>\n");
            sb.append("  <div class=\"tile-bar\">").append(barSvg(c.pass, c.fail, c.skip, 200)).append("</div>\n");
            sb.append("</a>\n");
        }
        sb.append("</div></main>\n");
        sb.append("</div>\n"); // .layout

        appendRunMetaScript(sb, metaJson);
        sb.append("<script src=\"app.js\"></script>\n");
        sb.append("</body></html>\n");
        write(file, sb);
    }

    /* ----------------------------- details.html ----------------------------- */

    private static void writeDetails(Path file, Counts overall, Map<String, List<Row>> bySlice,
                                     String metaJson) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>karate-js · test262 · details</title>
                <link rel="stylesheet" href="styles.css">
                </head>
                <body class="page-details">
                <header>
                  <h1>karate-js · test262 · details</h1>
                  <p><a href="index.html">&larr; back to index</a></p>
                  <div id="meta"></div>
                """);
        appendSummaryPills(sb, overall);
        sb.append("""
                  <div class="filters">
                    <input id="q" placeholder="filter by path substring…" />
                    <label><input type="checkbox" class="status-filter" data-s="PASS"> PASS</label>
                    <label><input type="checkbox" class="status-filter" data-s="FAIL" checked> FAIL</label>
                    <label><input type="checkbox" class="status-filter" data-s="SKIP"> SKIP</label>
                  </div>
                </header>
                <main id="details-root">
                """);

        for (Map.Entry<String, List<Row>> e : bySlice.entrySet()) {
            String slice = e.getKey();
            List<Row> rows = e.getValue();
            Counts c = countsOf(rows);
            sb.append("<section class=\"slice\" id=\"").append(escape(anchorOf(slice))).append("\">\n");
            sb.append("<h2>").append(escape(slice))
              .append(" <span class=\"slice-counts\">")
              .append("<span class=\"pill pass\">").append(c.pass).append("</span> ")
              .append("<span class=\"pill fail\">").append(c.fail).append("</span> ")
              .append("<span class=\"pill skip\">").append(c.skip).append("</span>")
              .append("</span></h2>\n");
            sb.append("<ul class=\"rows\">\n");
            for (Row r : rows) {
                sb.append("  <li class=\"row r-").append(r.status).append("\" data-path=\"")
                  .append(escape(r.path)).append("\" data-s=\"").append(r.status).append("\">");
                sb.append("<span class=\"icon\">").append(statusIcon(r.status)).append("</span> ");
                sb.append("<span class=\"path\">").append(escape(r.path)).append("</span>");
                if ("FAIL".equals(r.status)) {
                    if (r.errorType != null) {
                        sb.append(" <span class=\"err-type\">").append(escape(r.errorType)).append("</span>");
                    }
                    if (r.message != null) {
                        sb.append(" <span class=\"err-msg\">").append(escape(r.message)).append("</span>");
                    }
                } else if ("SKIP".equals(r.status) && r.reason != null) {
                    sb.append(" <span class=\"reason\">").append(escape(r.reason)).append("</span>");
                }
                sb.append("</li>\n");
            }
            sb.append("</ul>\n</section>\n");
        }

        sb.append("</main>\n");
        appendRunMetaScript(sb, metaJson);
        sb.append("<script src=\"app.js\"></script>\n");
        sb.append("</body></html>\n");
        write(file, sb);
    }

    /* -------------------------- shared HTML helpers -------------------------- */

    private static void appendSummaryPills(StringBuilder sb, Counts c) {
        sb.append("<div class=\"summary\">\n");
        sb.append(String.format(
                "  <span class=\"pill pass\">%d pass</span>%n" +
                "  <span class=\"pill fail\">%d fail</span>%n" +
                "  <span class=\"pill skip\">%d skip</span>%n" +
                "  <span class=\"pill total\">%d total</span>%n",
                c.pass, c.fail, c.skip, c.total()));
        sb.append("  <span class=\"bar\">").append(barSvg(c.pass, c.fail, c.skip, 200)).append("</span>\n");
        sb.append("</div>\n");
    }

    /** Embed run-meta as a JSON island. HTML entities are NOT decoded inside
     *  {@code <script type="application/json">}, so we must NOT html-escape here
     *  — we only need to neutralize the byte sequence {@code </} (which would
     *  end the script element) by splitting it with a JSON-tolerated escape. */
    private static void appendRunMetaScript(StringBuilder sb, String metaJson) {
        sb.append("<script id=\"run-meta\" type=\"application/json\">")
          .append(metaJson.replace("</", "<\\/"))
          .append("</script>\n");
    }

    private static String statusIcon(String status) {
        return switch (status) {
            case "PASS" -> "✅";
            case "FAIL" -> "❌";
            case "SKIP" -> "⏭";
            default -> "·";
        };
    }

    private static String barSvg(int pass, int fail, int skip, int width) {
        int total = pass + fail + skip;
        if (total == 0) return "";
        int pw = (int) Math.round((double) width * pass / total);
        int fw = (int) Math.round((double) width * fail / total);
        int sw = width - pw - fw;
        return "<svg width=\"" + width + "\" height=\"10\" viewBox=\"0 0 " + width + " 10\">" +
                "<rect x=\"0\" width=\"" + pw + "\" height=\"10\" fill=\"#4caf50\"/>" +
                "<rect x=\"" + pw + "\" width=\"" + fw + "\" height=\"10\" fill=\"#e53935\"/>" +
                "<rect x=\"" + (pw + fw) + "\" width=\"" + sw + "\" height=\"10\" fill=\"#9e9e9e\"/>" +
                "</svg>";
    }

    /* ------------------------------- tree ------------------------------- */

    private record DirNode(String name,
                           Map<String, DirNode> children,
                           List<Row> tests) {
        DirNode(String name) { this(name, new LinkedHashMap<>(), new ArrayList<>()); }
    }

    private static void addToTree(DirNode root, String[] parts, int idx, Row r) {
        DirNode cur = root;
        for (int i = idx; i < parts.length - 1; i++) {
            String seg = parts[i];
            cur = cur.children.computeIfAbsent(seg, DirNode::new);
        }
        cur.tests.add(r);
    }

    /** Render the tree as nested {@code <details>}. Top 2 levels open by
     *  default ("uncollapsed at a glance"); deeper levels collapsed.
     *  Depth-2 tree nodes (= a slice key like {@code built-ins/Array}) link
     *  to {@code details.html#<anchor>}; other depths show plain text since
     *  they don't correspond to a single details.html section. */
    private static void renderTree(StringBuilder sb, DirNode node, int depth, List<String> pathToHere) {
        int[] counts = totals(node);
        int pass = counts[0], fail = counts[1], skip = counts[2];

        // Slice link only at depth 2: pathToHere = [level1, level2], slice = "level1/level2"
        String slice = (depth == 2) ? String.join("/", pathToHere) : null;

        boolean openByDefault = depth <= 1;
        sb.append("<details").append(openByDefault ? " open" : "").append(">\n");
        sb.append("<summary>");
        if (slice != null) {
            sb.append("<a href=\"details.html#").append(escape(anchorOf(slice))).append("\">")
              .append(escape(node.name)).append("</a>");
        } else {
            sb.append("<span class=\"dir\">").append(escape(node.name)).append("</span>");
        }
        sb.append(" <span class=\"counts\">").append(pass).append(" / ").append(fail).append(" / ").append(skip).append("</span>");
        sb.append("</summary>\n");

        List<String> sortedChildren = new ArrayList<>(node.children.keySet());
        Collections.sort(sortedChildren);
        for (String c : sortedChildren) {
            List<String> next = new ArrayList<>(pathToHere);
            next.add(c);
            renderTree(sb, node.children.get(c), depth + 1, next);
        }
        sb.append("</details>\n");
    }

    private static int[] totals(DirNode n) {
        int p = 0, f = 0, s = 0;
        for (Row r : n.tests) {
            switch (r.status) { case "PASS" -> p++; case "FAIL" -> f++; case "SKIP" -> s++; }
        }
        for (DirNode c : n.children.values()) {
            int[] sub = totals(c);
            p += sub[0]; f += sub[1]; s += sub[2];
        }
        return new int[] { p, f, s };
    }

    private static Counts countsOf(List<Row> rows) {
        int p = 0, f = 0, s = 0;
        for (Row r : rows) {
            switch (r.status) { case "PASS" -> p++; case "FAIL" -> f++; case "SKIP" -> s++; }
        }
        return new Counts(p, f, s);
    }

    /* ------------------------------- util ------------------------------- */

    private static String between(String line, String left, String right) {
        int i = line.indexOf(left);
        if (i < 0) return null;
        int s = i + left.length();
        int e = line.indexOf(right, s);
        return e < 0 ? null : line.substring(s, e);
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static void write(Path file, StringBuilder sb) throws IOException {
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void copyResource(String resource, Path dest) throws IOException {
        try (InputStream in = Test262Report.class.getResourceAsStream(resource)) {
            if (in == null) {
                Files.writeString(dest, "/* missing resource: " + resource + " */\n", StandardCharsets.UTF_8);
                return;
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
