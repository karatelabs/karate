package io.karatelabs.js.test262;

import io.karatelabs.js.Engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads test262 harness helpers (assert.js, sta.js, plus the per-test {@code includes}).
 * <p>
 * Harness files are read from {@code <test262>/harness/} and cached in memory since
 * the same ~40 helpers are loaded over and over across the suite. Thread-safe —
 * the cache is a {@link ConcurrentHashMap} so one instance can be shared across
 * all worker threads in parallel runs.
 */
public final class HarnessLoader {

    private final Path harnessDir;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public HarnessLoader(Path test262Root) {
        this.harnessDir = test262Root.resolve("harness");
    }

    /** Always-loaded helpers mandated by test262 (see CONTRIBUTING.md). */
    public static final List<String> DEFAULT_HELPERS = List.of("assert.js", "sta.js");

    /** Loads default helpers plus the test's declared {@code includes}, in that order. */
    public void primeEngine(Engine engine, List<String> testIncludes) {
        for (String h : DEFAULT_HELPERS) {
            evalHelper(engine, h);
        }
        for (String h : testIncludes) {
            if (DEFAULT_HELPERS.contains(h)) continue; // already loaded
            evalHelper(engine, h);
        }
    }

    private void evalHelper(Engine engine, String name) {
        String src = cache.computeIfAbsent(name, this::readHelper);
        engine.eval(src);
    }

    private String readHelper(String name) {
        Path p = harnessDir.resolve(name);
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new RuntimeException("missing harness helper: " + p, e);
        }
    }
}
