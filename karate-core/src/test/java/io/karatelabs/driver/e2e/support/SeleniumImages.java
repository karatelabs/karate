/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver.e2e.support;

import org.testcontainers.utility.DockerImageName;

/**
 * Resolves Selenium container image names for the current host architecture.
 *
 * <p>Google does not ship Chrome for arm64 Linux, so the upstream {@code selenium/*}
 * images are amd64-only. Running them on Apple Silicon falls back to QEMU emulation
 * — Chrome under emulation is roughly 5–10× slower than native and frequently times
 * out. The community {@code seleniarm/*} images are arm64 builds backed by Chromium
 * and are drop-in compatible with the Selenium wire protocol.</p>
 *
 * <p>Selection rule:</p>
 * <ol>
 *   <li>Env vars {@code KARATE_SELENIUM_STANDALONE_IMAGE} /
 *       {@code KARATE_SELENIUM_HUB_IMAGE} / {@code KARATE_SELENIUM_NODE_IMAGE} win
 *       if set — escape hatch for unusual environments.</li>
 *   <li>Otherwise, host {@code os.arch == aarch64} (Apple Silicon, arm64 Linux)
 *       resolves to {@code seleniarm/*}.</li>
 *   <li>Everything else (GitHub Actions {@code ubuntu-latest}, x86 hosts) resolves
 *       to upstream {@code selenium/*}.</li>
 * </ol>
 */
public final class SeleniumImages {

    private static final boolean ARM64 = "aarch64".equals(System.getProperty("os.arch"));

    private SeleniumImages() {
    }

    /**
     * Standalone image — used by {@code W3cDriverFeatureTest} and {@code W3cFrameFeatureTest}.
     * Returns a {@code DockerImageName} because {@code BrowserWebDriverContainer} enforces an
     * image-name allowlist; the seleniarm arm64 image needs an explicit
     * {@code asCompatibleSubstituteFor("selenium/standalone-chrome")} declaration to be
     * accepted. On amd64 the substitute-for is a no-op (selenium/standalone-chrome
     * substituting for itself).
     */
    public static DockerImageName standalone() {
        String image = resolve("KARATE_SELENIUM_STANDALONE_IMAGE",
                "seleniarm/standalone-chromium:latest",
                "selenium/standalone-chrome:latest");
        return DockerImageName.parse(image).asCompatibleSubstituteFor("selenium/standalone-chrome");
    }

    /** Hub image — used by {@code W3cGridE2eTest}. */
    public static String hub() {
        return resolve("KARATE_SELENIUM_HUB_IMAGE",
                "seleniarm/hub:latest",
                "selenium/hub:latest");
    }

    /** Node image — used by {@code W3cGridE2eTest}. */
    public static String node() {
        return resolve("KARATE_SELENIUM_NODE_IMAGE",
                "seleniarm/node-chromium:latest",
                "selenium/node-chromium:latest");
    }

    private static String resolve(String envVar, String arm64Image, String amd64Image) {
        String override = System.getenv(envVar);
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return ARM64 ? arm64Image : amd64Image;
    }

}
