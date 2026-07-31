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
     * Discover + evaluate {@code karate-boot.js} for a Suite <b>under construction</b> — called from
     * the {@link Suite} constructor, before config discovery and feature resolution, because
     * {@code boot.classpath(dir)} is what both of those honour. Returns the {@link BootBinding} so
     * the Suite can read the declared mapping and later surface registered exts on
     * {@code SUITE_ENTER}. Returns {@code null} when no boot file is present (the common case —
     * boot is opt-in).
     *
     * <p>The boot file lives at the <b>project root</b> — and discovery only ever looks where the USER
     * named the project: {@code bootstrapWorkingDir} (the explicitly-set working dir, else the explicitly-set
     * config dir, else the process CWD), then the real classpath. It deliberately does NOT follow the
     * classloader-probed {@link Suite#getRoot()}: a Java project's root is {@code target/test-classes}, and
     * booting a file that landed there as a copied resource would change behaviour for a project that never
     * asked for it. It also cannot depend on {@code suite.getWorkingDir()} — that is assigned <i>after</i>
     * boot, precisely because boot may re-anchor it.</p>
     *
     * @param suite               the Suite being constructed — {@code getRoot()} is already final
     * @param bootstrapWorkingDir where to look for the boot file
     * @param env                 the {@code karate.env} value, exposed as {@code boot.env}
     * @throws RuntimeException when boot is present but evaluation fails (Suite
     *                          must fail loud per K43).
     */
    public static BootBinding evalIfPresent(Suite suite, Path bootstrapWorkingDir, String env) {
        Resource resource = locate(bootstrapWorkingDir);
        if (resource == null) {
            return null;
        }
        logger.info("{} processed", BOOT_FILE_NAME);
        BootBinding boot = new BootBinding(suite, suite == null ? bootstrapWorkingDir : suite.getRoot(), env,
                suite == null ? e -> {} : suite::registerExtListener);
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
     * Suite construction, so callers don't have to) anchored at {@code workingDir}, and returns the
     * binding the Suite constructor already evaluated. Returns {@code null} when {@code workingDir} is
     * null or no {@code karate-boot.js} is present (the no-ext zero-cost path is preserved).</p>
     *
     * @throws RuntimeException when a boot file IS present but its evaluation fails (fail-loud per K43).
     */
    public static BootBinding bootOnly(Path workingDir, String env) {
        if (workingDir == null) {
            return null;
        }
        Runner.Builder builder = Runner.builder()
                .configDir(workingDir.toString())
                .workingDir(workingDir);
        if (env != null && !env.isBlank()) {
            builder.karateEnv(env);
        }
        return builder.buildSuite().getBootBinding();
    }

    private static Resource locate(Path bootstrapWorkingDir) {
        // 1. The dir the user named as the project (typical: customer drops karate-boot.js next to
        //    pom.xml / karate-config.js).
        if (bootstrapWorkingDir != null) {
            Path bootAtRoot = bootstrapWorkingDir.resolve(BOOT_FILE_NAME);
            if (Files.exists(bootAtRoot)) {
                return Resource.from(bootAtRoot, bootstrapWorkingDir);
            }
        }
        // 2. Classpath fallback (e.g. inside a JAR-bundled test suite).
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
