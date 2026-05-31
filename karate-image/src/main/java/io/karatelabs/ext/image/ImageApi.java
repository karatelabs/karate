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
import io.karatelabs.core.StepResult;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;
import io.karatelabs.output.LogContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The per-scenario {@code image} global (one instance per scenario, see
 * {@link ImageExt}). Holds a copy of the suite defaults plus the scenario's
 * {@link KarateJsContext}, so its config is scenario-scoped (parallel-safe) and it
 * resolves baseline / option-file / rebase paths through Karate's prefix system
 * ({@code this:}/{@code classpath:}/{@code file:}/relative) via the context.
 *
 * <p>API (IMAGE_SPIKE.md §3.7):</p>
 * <pre>
 *   image.baselineDir = 'baselines'                 // config: property-setter
 *   image.config = { threshold: 0.02, engine: 'resemble' }   // config: bulk overlay
 *   image.compare('home', latestBytes)              // name + explicit latest
 *   image.compare({ name: 'home', latest: bytes, threshold: 0.05 })   // one-shot map
 *   image.rebase('home', latestBytes)               // overwrite the baseline
 * </pre>
 *
 * <p>{@code compare} resolves the baseline at {@code <baselineDir>/<name>.png},
 * auto-loads options from {@code <baselineDir>/<name>.json}, auto-establishes a
 * missing baseline (writes latest, passes), fails the step on mismatch unless
 * {@code failOnMismatch:false}, returns a result map, and emits the multi-part
 * {@code image-comparison} embed (baseline/latest/diff + meta).</p>
 */
public class ImageApi implements SimpleObject {

    private static final Set<String> STRUCTURAL = Set.of("name", "latest", "baseline");
    private static final String PNG = "image/png";

    private final Map<String, Object> config;   // per-scenario copy of suite defaults
    private final KarateJsContext context;

    ImageApi(Map<String, Object> defaults, KarateJsContext context) {
        this.config = new LinkedHashMap<>(defaults);   // copy → scenario isolation
        this.context = context;
    }

    // ---- SimpleObject: config props + the compare/rebase verbs ----

    @Override
    public Object jsGet(String name) {
        switch (name) {
            case "compare":
                return (JavaInvokable) this::compare;
            case "rebase":
                return (JavaInvokable) this::rebase;
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

    // ---- commands ----

    private Object compare(Object... args) {
        Map<String, Object> call = parseArgs(args);
        String name = requireName(call, "compare");
        byte[] latest = requireLatest(call, name, "compare");
        String base = stripPng(name);

        String baselinePath = call.containsKey("baseline") ? str(call.get("baseline")) : path(base + ".png");
        byte[] baseline = readBytes(baselinePath);

        if (baseline == null) {
            // auto-establish: no baseline yet → write latest as the new baseline, pass
            writeBytes(baselinePath, latest);
            emit(name, latest, latest, null, meta(name, 0.0, true, true));
            return result(name, true, 0.0, false, true);
        }

        Map<String, Object> options = new LinkedHashMap<>(loadNamedOptions(base));
        for (Map.Entry<String, Object> e : call.entrySet()) {
            if (!STRUCTURAL.contains(e.getKey())) {
                options.put(e.getKey(), e.getValue());
            }
        }
        if (options.containsKey("threshold")) {
            options.put("failureThreshold", options.remove("threshold"));
        }
        options.put("name", name);

        try {
            Map<String, Object> r = ImageComparison.compare(baseline, latest, options, defaultOptions());
            double pct = num(r.get("mismatchPercentage"));
            emit(name, baseline, latest, diffBytes(r), meta(name, pct, true, false));
            return result(name, true, pct, false, false);
        } catch (ImageComparison.MismatchException e) {
            double pct = num(e.data.get("mismatchPercentage"));
            emit(name, baseline, latest, diffBytes(e.data), meta(name, pct, false, false));
            if (failOnMismatch()) {
                throw new RuntimeException("image.compare('" + name + "'): " + e.getMessage());
            }
            return result(name, false, pct, Boolean.TRUE.equals(e.data.get("isMismatch")), false);
        }
    }

    private Object rebase(Object... args) {
        Map<String, Object> call = parseArgs(args);
        String name = requireName(call, "rebase");
        byte[] latest = requireLatest(call, name, "rebase");
        String baselinePath = call.containsKey("baseline") ? str(call.get("baseline")) : path(stripPng(name) + ".png");
        writeBytes(baselinePath, latest);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("rebased", true);
        return r;
    }

    // ---- options / config mapping ----

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

    private Map<String, Object> loadNamedOptions(String base) {
        String jsonPath = path(base + ".json");
        try {
            Resource r = resolve(jsonPath);
            if (!r.exists()) {
                return new LinkedHashMap<>();
            }
            try (InputStream is = r.getStream()) {
                return Json.of(new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).asMap();
            }
        } catch (Exception e) {
            throw new RuntimeException("image: failed to read options '" + jsonPath + "': " + e.getMessage(), e);
        }
    }

    private boolean failOnMismatch() {
        Object v = config.get("failOnMismatch");
        return v == null || Boolean.parseBoolean(String.valueOf(v));
    }

    // ---- path resolution (reuses Karate's prefix system) ----

    /** Resolve a path string through the prefix system: this:/classpath:/file:/relative. */
    private Resource resolve(String p) {
        if (p.startsWith(Resource.THIS_COLON)) {
            return context.getWorkingDir().resolve(p.substring(Resource.THIS_COLON.length()));
        }
        return Resource.path(p);
    }

    /** {@code <baselineDir>/<leaf>}, or just {@code <leaf>} when no baselineDir is set. */
    private String path(String leaf) {
        String dir = str(config.get("baselineDir"));
        if (dir == null || dir.isEmpty()) {
            return leaf;
        }
        return dir.endsWith("/") ? dir + leaf : dir + "/" + leaf;
    }

    private byte[] readBytes(String p) {
        try {
            Resource r = resolve(p);
            if (!r.exists()) {
                return null;
            }
            try (InputStream is = r.getStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void writeBytes(String p, byte[] bytes) {
        try {
            Path target = resolve(p).getPath();
            if (target == null) {
                throw new RuntimeException("not a writable file path: " + p);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, bytes);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("image: failed to write '" + p + "': " + e.getMessage(), e);
        }
    }

    // ---- embed + result shaping ----

    private void emit(String name, byte[] baseline, byte[] latest, byte[] diff, Map<String, Object> meta) {
        java.util.List<StepResult.Part> parts = new java.util.ArrayList<>();
        if (baseline != null) {
            parts.add(new StepResult.Part("baseline", PNG, baseline));
        }
        if (latest != null) {
            parts.add(new StepResult.Part("latest", PNG, latest));
        }
        if (diff != null) {
            parts.add(new StepResult.Part("diff", PNG, diff));
        }
        LogContext.get().embed(new StepResult.Embed("image-comparison", parts, meta));
    }

    private static byte[] diffBytes(Map<String, Object> resultData) {
        Object img = resultData.get(ImageComparison.DIFF_IMAGE);
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

    private Map<String, Object> meta(String name, double pct, boolean passed, boolean established) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("mismatchPercentage", pct);
        m.put("threshold", config.getOrDefault("threshold", 0.0));
        m.put("engine", config.getOrDefault("engine", "resemble"));
        m.put("passed", passed);
        if (established) {
            m.put("baselineEstablished", true);
        }
        return m;
    }

    private static Map<String, Object> result(String name, boolean pass, double pct,
                                              boolean isMismatch, boolean established) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("pass", pass);
        r.put("mismatchPercentage", pct);
        r.put("isMismatch", isMismatch);
        r.put("baselineEstablished", established);
        return r;
    }

    // ---- arg parsing + small helpers ----

    private static Map<String, Object> parseArgs(Object... args) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (args.length == 1 && args[0] instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        if (args.length > 0) {
            out.put("name", args[0]);
        }
        if (args.length > 1) {
            out.put("latest", args[1]);
        }
        return out;
    }

    private static String requireName(Map<String, Object> call, String op) {
        String name = str(call.get("name"));
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("image." + op + ": 'name' is required");
        }
        return name;
    }

    private byte[] requireLatest(Map<String, Object> call, String name, String op) {
        byte[] latest = toBytes(call.get("latest"));
        if (latest == null) {
            throw new RuntimeException("image." + op + "('" + name + "'): 'latest' image (bytes or path) is required");
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
        // A Uint8Array (driver screenshots / new Uint8Array(...)) is a JsValue whose
        // getJavaValue() is byte[]. Top-level args are already unwrapped to byte[] at
        // the external-call boundary; this branch also covers a Uint8Array nested in
        // the map form (compare({ latest: ... })), where nested values aren't unwrapped.
        if (o instanceof io.karatelabs.js.JsValue jv) {
            return toBytes(jv.getJavaValue());
        }
        if (o instanceof String s) {
            return readBytes(s);
        }
        throw new RuntimeException("image: unsupported 'latest' type "
                + o.getClass().getName() + " (expected a Uint8Array, byte[], or a path string)");
    }

    private static String stripPng(String name) {
        return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }
}
