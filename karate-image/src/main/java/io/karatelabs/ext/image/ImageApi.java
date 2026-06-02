/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.ext.image;

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.core.KarateJsContext;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-scenario {@code image} global (one instance per scenario, see {@link ImageExt}).
 * Holds a copy of the suite defaults plus the scenario's {@link KarateJsContext}, so its
 * config is scenario-scoped (parallel-safe) and it resolves baseline / options-file paths
 * through Karate's prefix system ({@code this:}/{@code classpath:}/{@code file:}/relative).
 *
 * <p>This is the <b>primitive</b> surface — orchestration (auto-establish, fail-the-step,
 * emit the embed) lives in an overridable scenario-scope JS recipe (see the README). The
 * two verbs:</p>
 * <pre>
 *   image.diff(name, latest)                 // name-aware: resolves baseline + &lt;name&gt; options
 *   image.diff('this:base.png', latest, {…}) // explicit baseline path + inline options
 *   image.diff({ baseline, latest, … })      // one-shot map form
 *   image.resolve(name)  // → { baselinePath, optionsPath } (absolute)
 * </pre>
 *
 * <p>{@code diff} is <b>pure</b>: it reads images, runs the engine, computes the diff PNG, and
 * returns a result map (verdict + per-engine numbers + {@code error:{message,type}} when not
 * passing) carrying a ready-to-emit {@code embed} payload (or {@code embed:null} when the
 * {@code report} setting says no diff is warranted). It never throws, never emits, never writes
 * files, and never establishes a baseline — the recipe decides all of that.</p>
 */
public class ImageApi implements SimpleObject {

    private static final Set<String> STRUCTURAL = Set.of("name", "latest", "baseline");
    private static final String[] IMAGE_EXTS = {"png", "jpg", "jpeg", "gif", "bmp", "webp"};

    private final Map<String, Object> config;   // per-scenario copy of suite defaults
    private final KarateJsContext context;

    ImageApi(Map<String, Object> defaults, KarateJsContext context) {
        this.config = new LinkedHashMap<>(defaults);   // copy → scenario isolation
        this.context = context;
    }

    // ---- SimpleObject: config props + the verbs ----

    @Override
    public Object jsGet(String name) {
        switch (name) {
            case "diff":
                return (JavaInvokable) this::diff;
            case "resolve":
                return (JavaInvokable) this::resolveVerb;
            case "write":
                return (JavaInvokable) this::writeVerb;
            case "config":
                return new LinkedHashMap<>(config);
            default:
                return config.get(name);
        }
    }

    @Override
    public void putMember(String name, Object value) {
        if ("config".equals(name) && value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> config.put(String.valueOf(k), v));
        } else {
            config.put(name, value);
        }
    }

    @Override
    public Collection<String> jsKeys() {
        return config.keySet();
    }

    // ---- diff (the pixel primitive) ----

    private Object diff(Object... args) {
        Map<String, Object> call = parseArgs(args);
        byte[] latest = requireLatest(call);

        // first arg is either a name (resolve baseline + <name> options) or an explicit
        // baseline path/bytes. The map form may carry `name` or `baseline` explicitly.
        String name = str(call.get("name"));
        Object baselineArg = call.get("baseline");
        String baselinePath = null;
        String optionsPath = null;
        byte[] baseline;

        Map<String, Object> options = new LinkedHashMap<>();
        if (baselineArg instanceof String s) {
            baselinePath = absolute(s);          // explicit path: no <name>.json auto-load
            baseline = readBytes(s);
        } else if (baselineArg != null) {
            baseline = toBytes(baselineArg);     // explicit baseline bytes / Uint8Array
        } else if (name != null) {
            Map<String, Object> resolved = resolve(name);
            baselinePath = str(resolved.get("baselinePath"));
            optionsPath = str(resolved.get("optionsPath"));
            options.putAll(loadOptionsFile(optionsPath));   // file opts: low precedence
            baseline = readBytes(baselinePath);
        } else {
            throw new RuntimeException("image.diff: need a name or a baseline (and a latest)");
        }

        // per-call inline options (everything that isn't structural) override the file
        for (Map.Entry<String, Object> e : call.entrySet()) {
            if (!STRUCTURAL.contains(e.getKey())) {
                options.put(e.getKey(), e.getValue());
            }
        }
        if (options.containsKey("threshold")) {
            options.put("failureThreshold", options.remove("threshold"));
        }
        String reportName = name != null ? name : str(options.get("name"));
        if (reportName != null) {
            options.put("name", reportName);
        }

        Map<String, Object> r = ImageComparison.run(baseline, latest, options, defaultOptions());
        return result(r, reportName, options, baseline, latest, baselinePath, optionsPath);
    }

    /** Shape the engine result into the public {@code diff()} result + embed payload. */
    private Map<String, Object> result(Map<String, Object> r, String name, Map<String, Object> options,
                                       byte[] baseline, byte[] latest, String baselinePath, String optionsPath) {
        String error = str(r.get("error"));
        boolean pass = error == null;
        boolean baselineMissing = Boolean.TRUE.equals(r.get("isBaselineMissing"));
        boolean scaleMismatch = Boolean.TRUE.equals(r.get("isScaleMismatch"));
        boolean mismatch = Boolean.TRUE.equals(r.get("isMismatch"));
        double pct = num(r.get("mismatchPercentage"));
        double threshold = num(r.get("failureThreshold"));
        String engine = str(r.get("engine"));

        Map<String, Object> out = new LinkedHashMap<>();
        if (name != null) {
            out.put("name", name);
        }
        out.put("pass", pass);
        out.put("mismatchPercentage", pct);
        if (r.containsKey(ImageComparison.RESEMBLE_MISMATCH_PERCENT)) {
            out.put("resembleMismatchPercentage", num(r.get(ImageComparison.RESEMBLE_MISMATCH_PERCENT)));
        }
        if (r.containsKey(ImageComparison.SSIM_MISMATCH_PERCENT)) {
            out.put("ssimMismatchPercentage", num(r.get(ImageComparison.SSIM_MISMATCH_PERCENT)));
        }
        out.put("mismatch", mismatch);
        out.put("scaleMismatch", scaleMismatch);
        out.put("threshold", threshold);
        if (engine != null) {
            out.put("engine", engine);
        }
        if (!pass) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", error);
            err.put("type", baselineMissing ? "baselineMissing"
                    : scaleMismatch ? "scaleMismatch"
                    : mismatch ? "mismatch" : "error");
            out.put("error", err);
        }
        out.put("embed", embed(r, name, options, pass, baseline, latest, baselinePath, optionsPath));
        return out;
    }

    /**
     * Build the multi-part {@code embed} payload (in {@code karate.embed} object form), or
     * {@code null} when the {@code report} setting says no diff is warranted. The recipe tops
     * up {@code meta} with {@code latestPath} + {@code baselineEstablished} and forwards it.
     */
    private Map<String, Object> embed(Map<String, Object> r, String name, Map<String, Object> options,
                                      boolean pass, byte[] baseline, byte[] latest,
                                      String baselinePath, String optionsPath) {
        String report = str(config.getOrDefault("report", "mismatched"));
        boolean want = "all".equalsIgnoreCase(report) || ("mismatched".equalsIgnoreCase(report) && !pass);
        if (!want) {
            return null;
        }

        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        if (baseline != null) {
            parts.add(part("baseline", baseline));
        }
        if (latest != null) {
            parts.add(part("latest", latest));
        }
        byte[] diffBytes = pngBytes(r.get(ImageComparison.DIFF_IMAGE));
        if (diffBytes != null) {
            parts.add(part("diff", diffBytes));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (name != null) {
            meta.put("name", name);
        }
        meta.put("pass", pass);
        meta.put("mismatch", Boolean.TRUE.equals(r.get("isMismatch")));
        meta.put("scaleMismatch", Boolean.TRUE.equals(r.get("isScaleMismatch")));
        meta.put("mismatchPercentage", num(r.get("mismatchPercentage")));
        if (r.containsKey(ImageComparison.RESEMBLE_MISMATCH_PERCENT)) {
            meta.put("resembleMismatchPercentage", num(r.get(ImageComparison.RESEMBLE_MISMATCH_PERCENT)));
        }
        if (r.containsKey(ImageComparison.SSIM_MISMATCH_PERCENT)) {
            meta.put("ssimMismatchPercentage", num(r.get(ImageComparison.SSIM_MISMATCH_PERCENT)));
        }
        meta.put("threshold", num(r.get("failureThreshold")));
        meta.put("defaultThreshold", num(r.get("defaultFailureThreshold")));
        if (r.get("engine") != null) {
            meta.put("engine", r.get("engine"));
        }
        if (r.get("defaultEngine") != null) {
            meta.put("defaultEngine", r.get("defaultEngine"));
        }
        if (options.get("ignoredBoxes") != null) {
            meta.put("ignoredBoxes", options.get("ignoredBoxes"));
        }
        if (baselinePath != null) {
            meta.put("baselinePath", baselinePath);
        }
        if (optionsPath != null) {
            meta.put("optionsPath", optionsPath);
        }
        // Inline base64 of the source images — ONLY on this image-comparison embed (normal
        // screenshots / other embeds stay file-based, no bloat). The report lightbox feeds
        // these to client-side Resemble for live re-diff; data URLs are canvas-readable, so
        // tuning works even when the report is opened from file:// (unlike file-based <img>).
        if (baseline != null) {
            meta.put("baselineData", dataUrl(baseline));
        }
        if (latest != null) {
            meta.put("latestData", dataUrl(latest));
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("name", "image-comparison");
        embed.put("parts", parts);
        embed.put("meta", meta);
        return embed;
    }

    private static Map<String, Object> part(String role, byte[] data) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("role", role);
        p.put("data", data);   // mime auto-detected by karate.embed from the bytes
        return p;
    }

    // ---- resolve (name → absolute baseline + options paths) ----

    private Object resolveVerb(Object... args) {
        if (args.length == 0 || str(args[0]) == null) {
            throw new RuntimeException("image.resolve: 'name' is required");
        }
        return resolve(str(args[0]));
    }

    private Map<String, Object> resolve(String name) {
        String base = stripExt(name);
        boolean hasExt = !base.equals(name);
        String baselineLeaf = hasExt ? name : findBaselineLeaf(base);
        String baselineJoined = join(str(config.get("baselineDir")), baselineLeaf);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baselinePath", absolute(baselineJoined));
        out.put("optionsPath", absolute(join(optionsDir(), base + ".json")));
        out.put("baselineExists", resourceExists(baselineJoined));
        return out;
    }

    /**
     * {@code image.write(name|path, bytes)} — write image bytes to the resolved baseline
     * (by name) or to an explicit path; returns the absolute path written. The recipe uses
     * this for auto-establish and programmatic rebase ({@code karate.write} can't target an
     * absolute path outside the report output dir).
     */
    private Object writeVerb(Object... args) {
        if (args.length < 2) {
            throw new RuntimeException("image.write: needs (name|path, bytes)");
        }
        String first = str(args[0]);
        String path = looksLikePath(first) ? first : str(resolve(first).get("baselinePath"));
        writeBytes(path, toBytes(args[1]));
        return absolute(path);
    }

    /** Existing {@code <baselineDir>/<base>.<ext>} for any known image ext, else default png. */
    private String findBaselineLeaf(String base) {
        String dir = str(config.get("baselineDir"));
        for (String ext : IMAGE_EXTS) {
            String leaf = base + "." + ext;
            Resource res = resolveResource(join(dir, leaf));
            if (res != null && res.exists()) {
                return leaf;
            }
        }
        return base + ".png";
    }

    private String optionsDir() {
        String dir = str(config.get("optionsDir"));
        return dir != null ? dir : str(config.get("baselineDir"));
    }

    // ---- options file ----

    private Map<String, Object> loadOptionsFile(String optionsPath) {
        if (optionsPath == null) {
            return new LinkedHashMap<>();
        }
        Resource r = resolveResource(optionsPath);
        if (r == null || !r.exists()) {
            return new LinkedHashMap<>();   // missing options is never an error
        }
        try (InputStream is = r.getStream()) {
            return Json.of(new String(is.readAllBytes(), StandardCharsets.UTF_8)).asMap();
        } catch (Exception e) {
            throw new RuntimeException("image: failed to read options '" + optionsPath + "': " + e.getMessage(), e);
        }
    }

    /** Suite-level engine config passed to {@link ImageComparison} as defaultOptions. */
    private Map<String, Object> defaultOptions() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("engine", config.getOrDefault("engine", "resemble"));
        if (config.containsKey("threshold")) {
            d.put("failureThreshold", config.get("threshold"));
        }
        d.put("report", config.getOrDefault("report", "mismatched"));
        if (config.containsKey("allowScaling")) {
            d.put("allowScaling", config.get("allowScaling"));
        }
        return d;
    }

    // ---- path resolution (reuses Karate's prefix system) ----

    private Resource resolveResource(String p) {
        try {
            if (p.startsWith(Resource.THIS_COLON)) {
                return context.getWorkingDir().resolve(p.substring(Resource.THIS_COLON.length()));
            }
            return Resource.path(p);
        } catch (Exception e) {
            return null;
        }
    }

    /** Absolute filesystem path for command templates (rebase / write-options). */
    private String absolute(String p) {
        Resource r = resolveResource(p);
        if (r != null) {
            java.nio.file.Path path = r.getPath();
            if (path != null) {
                return path.toAbsolutePath().toString();
            }
        }
        return p;
    }

    private static String join(String dir, String leaf) {
        if (dir == null || dir.isEmpty()) {
            return leaf;
        }
        return dir.endsWith("/") ? dir + leaf : dir + "/" + leaf;
    }

    private byte[] readBytes(String p) {
        try {
            Resource r = resolveResource(p);
            if (r == null || !r.exists()) {
                return null;
            }
            try (InputStream is = r.getStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean resourceExists(String p) {
        Resource r = resolveResource(p);
        return r != null && r.exists();
    }

    private void writeBytes(String p, byte[] bytes) {
        try {
            Resource r = resolveResource(p);
            java.nio.file.Path target = r != null ? r.getPath() : null;
            if (target == null) {
                target = new java.io.File(p).toPath();
            }
            java.nio.file.Path parent = target.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            java.nio.file.Files.write(target, bytes);
        } catch (Exception e) {
            throw new RuntimeException("image.write: failed to write '" + p + "': " + e.getMessage(), e);
        }
    }

    // ---- arg parsing + small helpers ----

    private static Map<String, Object> parseArgs(Object... args) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (args.length == 1 && args[0] instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (args.length > 0) {
            // a bare-name String → resolved baseline + <name> options; a path-looking String
            // (this:/classpath:/file:/contains a slash) → explicit baseline; bytes → baseline
            Object first = args[0];
            if (first instanceof String s && !looksLikePath(s)) {
                out.put("name", s);
            } else {
                out.put("baseline", first);
            }
        }
        if (args.length > 1) {
            out.put("latest", args[1]);
        }
        if (args.length > 2 && args[2] instanceof Map<?, ?> opts) {
            opts.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    private static boolean looksLikePath(String s) {
        return s.startsWith(Resource.THIS_COLON) || s.startsWith("classpath:") || s.startsWith("file:")
                || s.indexOf('/') >= 0 || s.indexOf('\\') >= 0;
    }

    private byte[] requireLatest(Map<String, Object> call) {
        byte[] latest = toBytes(call.get("latest"));
        if (latest == null) {
            throw new RuntimeException("image.diff: 'latest' image (bytes or path) is required");
        }
        return latest;
    }

    private byte[] toBytes(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof byte[] b) {
            return b;
        }
        if (o instanceof io.karatelabs.js.JsValue jv) {
            return toBytes(jv.getJavaValue());
        }
        if (o instanceof String s) {
            return readBytes(s);
        }
        throw new RuntimeException("image: unsupported image value " + o.getClass().getName()
                + " (expected a Uint8Array, byte[], or a path string)");
    }

    /** A base64 {@code data:} URL for raw image bytes (mime sniffed; for client-side re-diff). */
    private static String dataUrl(byte[] b) {
        return "data:" + sniffMime(b) + ";base64," + java.util.Base64.getEncoder().encodeToString(b);
    }

    private static String sniffMime(byte[] b) {
        if (b != null && b.length >= 4) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF, b2 = b[2] & 0xFF, b3 = b[3] & 0xFF;
            if (b0 == 0x89 && b1 == 'P' && b2 == 'N' && b3 == 'G') return "image/png";
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg";
            if (b0 == 'G' && b1 == 'I' && b2 == 'F' && b3 == '8') return "image/gif";
            if (b0 == 'B' && b1 == 'M') return "image/bmp";
        }
        return "image/png";
    }

    private static byte[] pngBytes(Object img) {
        if (!(img instanceof BufferedImage bi)) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
