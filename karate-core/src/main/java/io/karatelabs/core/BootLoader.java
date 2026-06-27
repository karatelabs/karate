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
package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;
import io.karatelabs.js.ExternalBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Discovers {@code karate-boot.js} (workdir root, then classpath root) and evaluates
 * it once per Suite. The boot file is ext-scripting only — its JS scope is
 * discarded after evaluation; the side effects (exts registered on Suite) are the
 * only output. See AGENT_KARATE.md K43.
 */
public final class BootLoader {

    private static final Logger logger = LoggerFactory.getLogger(BootLoader.class);

    public static final String BOOT_FILE_NAME = "karate-boot.js";

    private BootLoader() {}

    /**
     * Discover + evaluate {@code karate-boot.js} for the given Suite. Returns the
     * {@link BootBinding} so the caller can surface registered exts on
     * {@code SUITE_ENTER}. Returns {@code null} when no boot file is present (the
     * common case — boot is opt-in).
     *
     * @throws RuntimeException when boot is present but evaluation fails (Suite
     *                          must fail loud per K43).
     */
    public static BootBinding loadIfPresent(Suite suite, String env) {
        Resource resource = locate(suite);
        if (resource == null) {
            return null;
        }
        logger.info("{} processed", BOOT_FILE_NAME);
        BootBinding boot = new BootBinding(suite, env);
        Engine engine = new Engine();
        // ExternalBridge enables reflective dispatch for plain Java methods on
        // the bound objects — required for boot.ext(name) / boot.read(path) etc.
        engine.setExternalBridge(new ExternalBridge() {});
        engine.putRootBinding("boot", boot);
        try {
            engine.eval(resource);
        } catch (Exception e) {
            // Boot-time failure is fatal per K43 — re-throw as RuntimeException so
            // Suite.run()'s caller sees it.
            throw new RuntimeException(
                    "karate-boot.js evaluation failed: " + e.getMessage(), e);
        }
        return boot;
    }

    /**
     * Boot-only evaluation: run {@code karate-boot.js} for a project working dir and return the
     * {@link BootBinding} <b>without running any features</b> (no {@code SUITE_ENTER}/{@code SUITE_EXIT},
     * no scenarios). The boot side effects — exts constructed + configured via {@code boot.ext(name)} +
     * {@code .putMember(...)} during the JS eval — are the whole point, so a caller that lives outside a
     * run (e.g. a persistent serve engine re-deriving a project's per-run {@code cov.*} config) can reach
     * the booted, configured exts on demand.
     *
     * <p>Constructs the minimal {@link Suite} the boot phase needs (this package owns the package-private
     * Suite construction, so callers don't have to) anchored at {@code workingDir} for both config + boot
     * discovery, then delegates to {@link #loadIfPresent}. Returns {@code null} when {@code workingDir} is
     * null or no {@code karate-boot.js} is present (the no-ext zero-cost path is preserved).</p>
     *
     * @throws RuntimeException when a boot file IS present but its evaluation fails (fail-loud per K43).
     */
    public static BootBinding bootOnly(Path workingDir, String env) {
        if (workingDir == null) {
            return null;
        }
        Suite suite = Runner.builder()
                .configDir(workingDir.toString())
                .workingDir(workingDir)
                .buildSuite();
        return loadIfPresent(suite, env);
    }

    private static Resource locate(Suite suite) {
        // 1. Workdir root (typical: customer drops karate-boot.js next to pom.xml /
        //    karate-config.js).
        Path workingDir = suite.getWorkingDir();
        if (workingDir != null) {
            Path bootAtRoot = workingDir.resolve(BOOT_FILE_NAME);
            if (Files.exists(bootAtRoot)) {
                return Resource.from(bootAtRoot);
            }
        }
        // 2. Classpath root fallback (e.g. inside a JAR-bundled test suite).
        try {
            Resource r = Resource.path("classpath:" + BOOT_FILE_NAME);
            if (r.exists()) {
                return r;
            }
        } catch (Exception e) {
            // not found on classpath either
        }
        return null;
    }
}
