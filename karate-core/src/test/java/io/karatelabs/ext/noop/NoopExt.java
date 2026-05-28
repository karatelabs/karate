/*
 * Test-only fixture: a minimal ext that records onBoot/onShutdown calls so
 * ExtSpiTest can exercise the resolution + registration paths without pulling
 * a real commercial ext into karate-core's test classpath.
 */
package io.karatelabs.ext.noop;

import io.karatelabs.core.Ext;
import io.karatelabs.core.Suite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NoopExt implements Ext {

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
