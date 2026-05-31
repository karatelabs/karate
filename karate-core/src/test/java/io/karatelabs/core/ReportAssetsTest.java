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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation failure modes for the imperative {@link ReportAssets} spec
 * (EXT.md § Report assets — no JSON manifest; the spec is built in onBoot).
 * The DummyExt fixture ships real assets under {@code META-INF/karate-ext/}, so
 * the test classloader resolves them.
 */
class ReportAssetsTest {

    private final ClassLoader cl = getClass().getClassLoader();

    @Test
    void validSpecResolvesAndCopies(@TempDir Path tmp) throws Exception {
        ReportAssets a = ReportAssets.named("dummy")
                .js("static/ext.js").css("static/ext.css")
                .page("nav.pages", "Dummy", "pages/dummy.html");
        a.validateAndBind(cl);
        assertEquals("ext.js", a.jsHref(), "static/ prefix stripped for web path");
        assertEquals("ext.css", a.cssHref());

        a.copyTo(tmp);
        assertTrue(Files.exists(tmp.resolve("ext.js")));
        assertTrue(Files.exists(tmp.resolve("ext.css")));
        assertTrue(Files.exists(tmp.resolve("pages/dummy.html")), "page keeps its pages/ prefix");
    }

    @Test
    void jsIsRequired() {
        ReportAssets a = ReportAssets.named("x").css("static/ext.css");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> a.validateAndBind(cl));
        assertTrue(ex.getMessage().contains("js(...) is required"), ex.getMessage());
    }

    @Test
    void missingResourceFailsLoud() {
        ReportAssets a = ReportAssets.named("x").js("static/does-not-exist.js");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> a.validateAndBind(cl));
        assertTrue(ex.getMessage().contains("missing resource"), ex.getMessage());
    }
}
