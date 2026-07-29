/*
 * Test-only fixture: an ext whose NAME is plural while the global it binds is singular
 * ('gadgets' → Gadget) — the shape every real namespace-carrying ext has (the commercial
 * 'rules' ext binds Rule). MissingExtGlobalHintTest uses it to prove ExtHint reaches an
 * ext from the singular global a feature actually names.
 */
package io.karatelabs.ext.gadgets;

import io.karatelabs.core.Ext;
import io.karatelabs.core.Suite;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.SimpleObject;

import java.util.Collection;
import java.util.List;

public class GadgetsExt implements Ext {

    /** The bound global — one verb, enough for a feature to call it. */
    public static final class GadgetApi implements SimpleObject {

        @Override
        public Object jsGet(String name) {
            return "echo".equals(name) ? (JavaInvokable) args -> args.length > 0 ? args[0] : null : null;
        }

        @Override
        public Collection<String> jsKeys() {
            return List.of("echo");
        }
    }

    @Override
    public void onBoot(Suite suite) {
        suite.registerGlobal("Gadget", new GadgetApi());
    }
}
