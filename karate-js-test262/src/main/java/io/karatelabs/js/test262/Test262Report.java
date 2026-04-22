package io.karatelabs.js.test262;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads {@code results.jsonl} + {@code run-meta.json} and writes a static
 * HTML dashboard under {@code html/}. Fully static; no server, no runtime JSON
 * fetches. The dashboard page and per-failure drill-downs are self-contained.
 * <p>
 * Usage:
 * <pre>
 *   java io.karatelabs.js.test262.Test262Report \
 *       [--results results.jsonl] [--run-meta run-meta.json] \
 *       [--test262 test262] [--out html]
 * </pre>
 */
public final class Test262Report {

    public static void main(String[] args) throws Exception {
        Path results = Paths.get("results.jsonl");
        Path runMeta = Paths.get("run-meta.json");
        Path test262Dir = Paths.get("test262");
        Path outDir = Paths.get("html");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--results"   -> results = Paths.get(args[++i]);
                case "--run-meta"  -> runMeta = Paths.get(args[++i]);
                case "--test262"   -> test262Dir = Paths.get(args[++i]);
                case "--out"       -> outDir = Paths.get(args[++i]);
                case "-h", "--help" -> {
                    System.out.println("Usage: Test262Report [--results <p>] [--run-meta <p>] [--test262 <p>] [--out <dir>]");
                    return;
                }
                default -> { /* ignore */ }
            }
        }

        if (!Files.isRegularFile(results)) {
            System.err.println("results file not found: " + results.toAbsolutePath());
            System.err.println("run Test262Runner first");
            System.exit(2);
        }

        List<Row> rows = readResults(results);
        String metaJson = Files.isRegularFile(runMeta) ? Files.readString(runMeta) : "{}";

        Files.createDirectories(outDir);
        Files.createDirectories(outDir.resolve("fail"));

        copyResource("/report/styles.css", outDir.resolve("styles.css"));
        copyResource("/report/app.js", outDir.resolve("app.js"));

        writeIndex(outDir.resolve("index.html"), rows, metaJson);

        int failCount = 0;
        for (Row r : rows) {
            if ("FAIL".equals(r.status)) {
                failCount++;
                writeFailPage(outDir.resolve("fail").resolve(sanitizePath(r.path) + ".html"),
                        r, test262Dir);
            }
        }

        System.out.println("Wrote report to: " + outDir.toAbsolutePath());
        System.out.println("  " + rows.size() + " total rows");
        System.out.println("  " + failCount + " per-test FAIL pages");
    }

    /* ------------------------------- model ------------------------------- */

    record Row(String path, String status, String errorType, String message, String reason) {}

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
        // Already alphabetical in the file, but sort defensively.
        out.sort(Comparator.comparing(Row::path));
        return out;
    }

    /* ------------------------------ index.html ------------------------------ */

    private static void writeIndex(Path file, List<Row> rows, String metaJson) throws IOException {
        int pass = 0, fail = 0, skip = 0;
        // Top-level dir stats: test/<X>/...
        Map<String, int[]> topStats = new TreeMap<>();
        // Nested tree for drill-down
        DirNode root = new DirNode("test");

        for (Row r : rows) {
            switch (r.status) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                case "SKIP" -> skip++;
            }
            String[] parts = r.path.split("/");
            if (parts.length >= 2) {
                int[] s = topStats.computeIfAbsent(parts[1], k -> new int[3]);
                switch (r.status) { case "PASS" -> s[0]++; case "FAIL" -> s[1]++; case "SKIP" -> s[2]++; }
                if ("test".equals(parts[0])) {
                    addToTree(root, parts, 1, r);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <title>karate-js · test262 results</title>
            <link rel="stylesheet" href="styles.css">
            </head>
            <body>
            <header>
              <h1>karate-js &middot; test262</h1>
              <div id="meta"></div>
              <div class="summary">
            """);
        sb.append(String.format(
                "    <span class=\"pill pass\">%d pass</span>\n" +
                "    <span class=\"pill fail\">%d fail</span>\n" +
                "    <span class=\"pill skip\">%d skip</span>\n" +
                "    <span class=\"pill total\">%d total</span>\n",
                pass, fail, skip, pass + fail + skip));
        sb.append("  </div>\n");
        sb.append("  <div class=\"filters\">\n");
        sb.append("    <input id=\"q\" placeholder=\"filter by path substring…\" />\n");
        sb.append("    <label><input type=\"checkbox\" class=\"status-filter\" data-s=\"PASS\" checked> PASS</label>\n");
        sb.append("    <label><input type=\"checkbox\" class=\"status-filter\" data-s=\"FAIL\" checked> FAIL</label>\n");
        sb.append("    <label><input type=\"checkbox\" class=\"status-filter\" data-s=\"SKIP\" checked> SKIP</label>\n");
        sb.append("  </div>\n");
        sb.append("</header>\n");

        sb.append("<h2>By top-level suite</h2>\n<ul class=\"top-stats\">\n");
        for (Map.Entry<String, int[]> e : topStats.entrySet()) {
            int[] s = e.getValue();
            sb.append(String.format(
                    "  <li><strong>%s</strong> <span class=\"bar\">%s</span> <span class=\"counts\">%d / %d / %d</span></li>%n",
                    escape(e.getKey()), barSvg(s[0], s[1], s[2]), s[0], s[1], s[2]));
        }
        sb.append("</ul>\n");

        sb.append("<h2>Tree</h2>\n<div id=\"tree\">\n");
        renderTree(sb, root, 0);
        sb.append("</div>\n");

        // Embed run-meta as a JSON island. HTML entities are NOT decoded inside
        // <script type="application/json">, so we must NOT html-escape here.
        // We only need to neutralize the byte sequence "</" (which would end the script
        // element) by splitting it with a Unicode escape that JSON.parse tolerates.
        sb.append("<script id=\"run-meta\" type=\"application/json\">")
          .append(metaJson.replace("</", "<\\/"))
          .append("</script>\n");
        sb.append("<script src=\"app.js\"></script>\n");
        sb.append("</body></html>\n");

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String barSvg(int pass, int fail, int skip) {
        int total = pass + fail + skip;
        if (total == 0) return "";
        int pw = (int) Math.round(100.0 * pass / total);
        int fw = (int) Math.round(100.0 * fail / total);
        int sw = 100 - pw - fw;
        return "<svg width=\"100\" height=\"10\" viewBox=\"0 0 100 10\">" +
                "<rect x=\"0\" width=\"" + pw + "\" height=\"10\" fill=\"#4caf50\"/>" +
                "<rect x=\"" + pw + "\" width=\"" + fw + "\" height=\"10\" fill=\"#e53935\"/>" +
                "<rect x=\"" + (pw + fw) + "\" width=\"" + sw + "\" height=\"10\" fill=\"#9e9e9e\"/>" +
                "</svg>";
    }

    /* --------------------------- per-test FAIL page --------------------------- */

    private static void writeFailPage(Path outFile, Row r, Path test262Dir) throws IOException {
        Files.createDirectories(outFile.getParent());
        String src;
        try {
            src = Files.readString(test262Dir.resolve(r.path));
        } catch (IOException e) {
            src = "(unable to read source: " + e.getMessage() + ")";
        }
        String reproducer = "java io.karatelabs.js.test262.Test262Runner --single "
                + r.path + " -vv";

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n");
        sb.append("<title>").append(escape(r.path)).append(" — FAIL</title>\n");
        sb.append("<link rel=\"stylesheet\" href=\"../styles.css\">\n</head><body>\n");
        sb.append("<p><a href=\"../index.html\">&larr; back to index</a></p>\n");
        sb.append("<h1>").append(escape(r.path)).append("</h1>\n");
        sb.append("<div class=\"summary\"><span class=\"pill fail\">FAIL");
        if (r.errorType != null) sb.append(" — ").append(escape(r.errorType));
        sb.append("</span></div>\n");
        if (r.message != null) {
            sb.append("<h2>Error</h2>\n<pre class=\"error\">").append(escape(r.message)).append("</pre>\n");
        }
        sb.append("<h2>Reproducer</h2>\n<pre class=\"cmd\">").append(escape(reproducer)).append("</pre>\n");
        sb.append("<h2>Source</h2>\n<pre class=\"source\">").append(numbered(src)).append("</pre>\n");
        sb.append("</body></html>\n");
        Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String numbered(String src) {
        String[] lines = src.split("\n", -1);
        StringBuilder sb = new StringBuilder(src.length() + lines.length * 6);
        int width = String.valueOf(Math.max(1, lines.length)).length();
        for (int i = 0; i < lines.length; i++) {
            String num = String.valueOf(i + 1);
            while (num.length() < width) num = " " + num;
            sb.append("<span class=\"ln\">").append(num).append("</span>  ")
              .append(escape(lines[i])).append('\n');
        }
        return sb.toString();
    }

    /* ------------------------------- tree ------------------------------- */

    private record DirNode(String name,
                           Map<String, DirNode> children,
                           List<Row> tests) {
        DirNode(String name) { this(name, new LinkedHashMap<>(), new ArrayList<>()); }
    }

    private static void addToTree(DirNode root, String[] parts, int idx, Row r) {
        // path is "test/a/b/c.js". parts[0]="test" already at root.
        DirNode cur = root;
        for (int i = idx; i < parts.length - 1; i++) {
            String seg = parts[i];
            cur = cur.children.computeIfAbsent(seg, DirNode::new);
        }
        cur.tests.add(r);
    }

    private static void renderTree(StringBuilder sb, DirNode node, int depth) {
        int[] counts = totals(node);
        int pass = counts[0], fail = counts[1], skip = counts[2];
        String summary = "<span class=\"bar\">" + barSvg(pass, fail, skip) + "</span>"
                + " <span class=\"counts\">" + pass + " / " + fail + " / " + skip + "</span>";
        sb.append("<details").append(depth == 0 ? " open" : "").append(">\n");
        sb.append("<summary><span class=\"dir\">").append(escape(node.name)).append("</span> ").append(summary).append("</summary>\n");

        List<String> sortedChildren = new ArrayList<>(node.children.keySet());
        Collections.sort(sortedChildren);
        for (String c : sortedChildren) renderTree(sb, node.children.get(c), depth + 1);

        List<Row> tests = new ArrayList<>(node.tests);
        tests.sort(Comparator.comparing(Row::path));
        if (!tests.isEmpty()) {
            sb.append("<ul class=\"tests\">\n");
            for (Row r : tests) {
                sb.append("  <li class=\"t t-").append(r.status).append("\" data-path=\"").append(escape(r.path)).append("\">");
                sb.append("<span class=\"status\">").append(r.status).append("</span> ");
                if ("FAIL".equals(r.status)) {
                    sb.append("<a href=\"fail/").append(escape(sanitizePath(r.path))).append(".html\">");
                    sb.append(escape(basename(r.path)));
                    sb.append("</a>");
                    if (r.errorType != null) sb.append(" <em>").append(escape(r.errorType)).append("</em>");
                } else {
                    sb.append(escape(basename(r.path)));
                    if ("SKIP".equals(r.status) && r.reason != null) {
                        sb.append(" <em>").append(escape(r.reason)).append("</em>");
                    }
                }
                sb.append("</li>\n");
            }
            sb.append("</ul>\n");
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

    /* ------------------------------- util ------------------------------- */

    private static String between(String line, String left, String right) {
        int i = line.indexOf(left);
        if (i < 0) return null;
        int s = i + left.length();
        int e = line.indexOf(right, s);
        return e < 0 ? null : line.substring(s, e);
    }

    private static String basename(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static String sanitizePath(String p) {
        return p.replace('/', '_').replace('\\', '_');
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

    private static void copyResource(String resource, Path dest) throws IOException {
        try (java.io.InputStream in = Test262Report.class.getResourceAsStream(resource)) {
            if (in == null) {
                // Write a minimal default if the resource is missing (shouldn't happen in normal builds).
                Files.writeString(dest, "/* missing resource: " + resource + " */\n", StandardCharsets.UTF_8);
                return;
            }
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
