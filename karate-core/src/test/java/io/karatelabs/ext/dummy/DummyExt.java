/*
 * Test-only fixture: a minimal ext that exercises the Phase 2 SPI surface —
 * registers a SimpleObject global `dummy` in onBoot. Resolved by name convention
 * (io.karatelabs.ext.dummy.DummyExt) from `boot.ext('dummy')`. Kept in karate-core
 * test scope so the SPI mechanics can be verified without pulling a real ext in.
 */
package io.karatelabs.ext.dummy;

import io.karatelabs.core.Ext;
import io.karatelabs.core.ReportAssets;
import io.karatelabs.core.Suite;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DummyExt implements Ext {

    /**
     * The ext global put in scenario scope as `dummy`. A {@link SimpleObject} so
     * members cross into the JS engine natively (no reflection): {@code jsGet}
     * returns a {@link JavaInvokable} for methods and the stored value for
     * properties; {@code putMember} accepts property writes.
     */
    public static final class DummyApi implements SimpleObject {

        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object jsGet(String name) {
            // method: echo(x) returns its first argument unchanged
            if ("echo".equals(name)) {
                return (JavaInvokable) args -> args.length > 0 ? args[0] : null;
            }
            // everything else is a property read from local state
            return state.get(name);
        }

        @Override
        public void putMember(String name, Object value) {
            state.put(name, value);
        }

        @Override
        public Collection<String> jsKeys() {
            return state.keySet();
        }
    }

    @Override
    public void onBoot(Suite suite) {
        suite.registerGlobal("dummy", new DummyApi());
        // Imperative asset registration (no manifest.json) — paths resolve against
        // META-INF/karate-ext/ on this ext's classloader. builtForCore omitted: this
        // test fixture is always in lockstep with the core it runs against.
        suite.registerReportAssets(
                ReportAssets.named("dummy")
                        .js("static/ext.js")
                        .css("static/ext.css")
                        .page("nav.pages", "Dummy", "pages/dummy.html"),
                getClass().getClassLoader());
    }

    @Override
    public Map<String, Object> getManifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "dummy-v1");
        return m;
    }
}
