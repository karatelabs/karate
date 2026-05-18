/*
 * Test-only fixture: a minimal plugin that records onBoot/onShutdown calls so
 * PluginSpiTest can exercise the resolution + registration paths without pulling
 * a real commercial plugin into karate-core's test classpath.
 */
package io.karatelabs.plugins.noop;

import io.karatelabs.core.Plugin;
import io.karatelabs.core.Suite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NoopPlugin implements Plugin {

    public static final AtomicInteger bootCount = new AtomicInteger();
    public static final AtomicInteger shutdownCount = new AtomicInteger();

    @Override
    public void onBoot(Suite suite) {
        bootCount.incrementAndGet();
    }

    @Override
    public void onShutdown() {
        shutdownCount.incrementAndGet();
    }

    @Override
    public Map<String, Object> getManifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "noop-v1");
        return m;
    }
}
