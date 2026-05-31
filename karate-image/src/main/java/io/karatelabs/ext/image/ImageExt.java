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

import io.karatelabs.core.Ext;
import io.karatelabs.core.ReportAssets;
import io.karatelabs.core.Suite;
import io.karatelabs.js.SimpleObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The image-comparison ext. Resolved by name convention
 * ({@code io.karatelabs.ext.image.ImageExt}) from {@code boot.ext('image')}.
 *
 * <p>Two roles:</p>
 * <ul>
 *   <li><b>Boot-time config holder.</b> {@code boot.ext('image')} returns this
 *       object, so {@code karate-boot.js} sets suite-wide defaults on it
 *       ({@code image.baselineDir = 'baselines'} etc.) — captured here via
 *       {@link SimpleObject}.</li>
 *   <li><b>Per-scenario global registrar.</b> {@code onBoot} registers an
 *       {@link io.karatelabs.core.ExtGlobalFactory} so each scenario gets its own
 *       {@link ImageApi} seeded with a copy of these defaults plus the scenario's
 *       context — parallel-safe, and able to resolve {@code this:}/{@code classpath:}/
 *       {@code file:} paths. See the karate-image README.</li>
 * </ul>
 */
public class ImageExt implements Ext, SimpleObject {

    // Suite-wide defaults set at boot (single-threaded). Copied per scenario.
    private final Map<String, Object> defaults = new ConcurrentHashMap<>();

    @Override
    public void onBoot(Suite suite) {
        // Per-scenario instance: fresh ImageApi per scenario, seeded with a copy of
        // the boot defaults + that scenario's KarateJsContext.
        suite.registerGlobal("image", context -> new ImageApi(defaults, context));
        // Asset files are named after the ext (image.js / image.css), not a generic
        // ext.js, so each ext is self-identifying in browser DevTools / stack traces.
        // The per-ext dir (ext/image/) already prevents collisions; this is for DX.
        // Dest is ext/<name>/<source-leaf>, so naming the source aligns the URL too:
        // boot.ext('image') → ext/image/image.js.
        suite.registerReportAssets(
                ReportAssets.named("image")
                        .js("static/image.js")
                        .css("static/image.css"),
                getClass().getClassLoader());
    }

    // ---- boot-time config target (SimpleObject) ----

    @Override
    public Object jsGet(String name) {
        if ("config".equals(name)) {
            return new LinkedHashMap<>(defaults);
        }
        return defaults.get(name);
    }

    @Override
    public void putMember(String name, Object value) {
        if ("config".equals(name) && value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> defaults.put(String.valueOf(k), v));
        } else {
            defaults.put(name, value);
        }
    }

    @Override
    public Collection<String> jsKeys() {
        return defaults.keySet();
    }

    @Override
    public Map<String, Object> getManifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "image-v1");
        return m;
    }
}
