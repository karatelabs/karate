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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Imperative spec for one ext's report-asset contribution, built fluently inside
 * {@link Ext#onBoot(Suite)} and handed to
 * {@link Suite#registerReportAssets(ReportAssets, ClassLoader)} (see EXT.md § Report assets).
 *
 * <p>There is deliberately no {@code manifest.json} — the ext object is already live
 * at {@code onBoot}, so asset wiring is declared in one place (Java), type-safe, with
 * no JSON-vs-file drift. {@code getManifest()} on {@link Ext} still carries the
 * {@code SUITE_ENTER} metadata; this type carries only what the report writer needs.</p>
 *
 * <pre>
 * suite.registerReportAssets(
 *     ReportAssets.named("image")
 *         .js("static/ext.js")
 *         .css("static/ext.css")
 *         .page("nav.pages", "Image diffs", "pages/image-comparison.html"),
 *     getClass().getClassLoader());
 * </pre>
 *
 * <p>There is no core-version guard here yet (D8's strict-match) — exts ship in
 * lockstep with core for now; revisit if independently-versioned drop-in JARs
 * make a mismatch likely.</p>
 *
 * <p>Asset paths are resolved against {@link #BASE} on the ext's classloader, so a
 * {@code .js("static/ext.js")} reads {@code META-INF/karate-ext/static/ext.js} and is
 * copied to {@code target/karate-reports/ext/<name>/ext.js} (the {@code static/} prefix
 * is stripped for the web path; {@code pages/} is kept).</p>
 */
public final class ReportAssets {

    /** Resource root inside an ext JAR for all asset paths in this spec. */
    public static final String BASE = "META-INF/karate-ext/";
    private static final String STATIC_PREFIX = "static/";

    private final String name;
    private String js;                 // required; e.g. "static/ext.js"
    private String css;                // optional; e.g. "static/ext.css"
    private final List<Page> pages = new ArrayList<>();

    // Bound at registration time by Suite.registerReportAssets.
    private ClassLoader classLoader;

    /** One nav-page (or other slot) contribution. */
    public record Page(String slot, String title, String href) {}

    private ReportAssets(String name) {
        this.name = name;
    }

    /** Start a spec for the ext registered under {@code name}. */
    public static ReportAssets named(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ReportAssets.named: name is null or empty");
        }
        return new ReportAssets(name);
    }

    /** The JS entry registered in {@code alpine:init} (D5). Required. */
    public ReportAssets js(String path) {
        this.js = path;
        return this;
    }

    /** Optional stylesheet, loaded if set. */
    public ReportAssets css(String path) {
        this.css = path;
        return this;
    }

    /** Add a slot page contribution (e.g. {@code nav.pages}). */
    public ReportAssets page(String slot, String title, String href) {
        pages.add(new Page(slot, title, href));
        return this;
    }

    public String name() {
        return name;
    }

    public List<Page> pages() {
        return Collections.unmodifiableList(pages);
    }

    /** Web-relative path of the JS entry, {@code static/} stripped. */
    public String jsHref() {
        return stripStatic(js);
    }

    /** Web-relative path of the CSS, {@code static/} stripped; null when no CSS. */
    public String cssHref() {
        return css == null ? null : stripStatic(css);
    }

    /**
     * Validate the spec against {@code classLoader} and bind it for later copying.
     * Throws (failing the Suite at boot) when {@code js} is unset or a referenced
     * resource is missing.
     */
    void validateAndBind(ClassLoader classLoader) {
        if (js == null || js.isBlank()) {
            throw new RuntimeException("ext '" + name + "': ReportAssets.js(...) is required");
        }
        requireResource(classLoader, js, "js");
        if (css != null) {
            requireResource(classLoader, css, "css");
        }
        for (Page p : pages) {
            if (p.href() != null) {
                requireResource(classLoader, p.href(), "page");
            }
        }
        this.classLoader = classLoader;
    }

    /**
     * Copy the declared assets (JS, optional CSS, page HTML) into {@code extDir}
     * ({@code target/karate-reports/ext/<name>/}). {@code static/} prefixes are
     * stripped so {@code static/ext.js} lands at {@code <extDir>/ext.js}; pages keep
     * their {@code pages/} prefix.
     */
    public void copyTo(Path extDir) throws IOException {
        Files.createDirectories(extDir);
        copyResource(js, extDir.resolve(stripStatic(js)));
        if (css != null) {
            copyResource(css, extDir.resolve(stripStatic(css)));
        }
        for (Page p : pages) {
            if (p.href() != null) {
                copyResource(p.href(), extDir.resolve(p.href()));
            }
        }
    }

    private void copyResource(String path, Path target) throws IOException {
        try (InputStream is = classLoader.getResourceAsStream(BASE + path)) {
            if (is == null) {
                throw new IOException("ext '" + name + "': resource vanished after validation: " + path);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void requireResource(ClassLoader cl, String path, String key) {
        try (InputStream is = cl.getResourceAsStream(BASE + path)) {
            if (is == null) {
                throw new RuntimeException("ext '" + name + "': " + key
                        + " points at missing resource: " + BASE + path);
            }
        } catch (IOException e) {
            throw new RuntimeException("ext '" + name + "': failed reading " + BASE + path + " — " + e.getMessage(), e);
        }
    }

    private static String stripStatic(String p) {
        return p.startsWith(STATIC_PREFIX) ? p.substring(STATIC_PREFIX.length()) : p;
    }
}
