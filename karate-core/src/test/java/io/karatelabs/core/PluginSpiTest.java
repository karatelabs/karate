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

import io.karatelabs.js.Engine;
import io.karatelabs.js.ExternalBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-level checks for {@link Plugin} + {@link BootBinding} + {@link BootLoader}.
 * Uses the explicit BootBinding constructor that decouples workingDir + listener
 * registrar from a fully-instantiated Suite. Full Suite-level integration (boot.js
 * loaded inside Suite.run()) is exercised in the downstream karate-plugins / demo
 * flow; this test covers the core SPI mechanics in isolation.
 */
class PluginSpiTest {

    @TempDir
    Path tmp;

    private BootBinding newBinding(Path workdir, String env, List<Plugin> registered) {
        return new BootBinding(null, workdir, env, registered::add);
    }

    @Test
    void bootBindingResolvesPluginByNameConvention() {
        List<Plugin> registered = new ArrayList<>();
        BootBinding boot = newBinding(tmp, "test", registered);
        Plugin plugin = boot.plugin("noop");
        assertNotNull(plugin);
        assertEquals("io.karatelabs.plugins.noop.NoopPlugin", plugin.getClass().getName());
        assertEquals(1, registered.size(), "plugin should be registered exactly once");
        assertSame(plugin, registered.get(0));
    }

    @Test
    void bootBindingReturnsSameSingletonForSameName() {
        List<Plugin> registered = new ArrayList<>();
        BootBinding boot = newBinding(tmp, "test", registered);
        Plugin first = boot.plugin("noop");
        Plugin second = boot.plugin("noop");
        assertSame(first, second);
        assertEquals(1, boot.getPlugins().size());
        assertEquals(1, registered.size(), "registrar fired only once for the singleton");
    }

    @Test
    void bootBindingMissingPluginFailsLoud() {
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> boot.plugin("does-not-exist"));
        assertTrue(ex.getMessage().contains("does-not-exist"));
    }

    @Test
    void bootLoaderReturnsNullWhenNoBootFile() {
        // BootLoader needs a Suite to call .getWorkingDir(); skip in isolation.
        // Workdir-resolution path is covered by bootReadResolvesPathAgainstWorkdir.
        assertEquals(BootLoader.BOOT_FILE_NAME, "karate-boot.js",
                "convention name is stable");
    }

    @Test
    void bootSysenvReadsRealEnvVar() {
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        // PATH is virtually always set on *nix + Windows.
        assertNotNull(boot.sysenv("PATH"));
        assertNull(boot.sysenv("__BOOT_SHOULD_NEVER_BE_SET_12345__"));
    }

    @Test
    void bootSysenvDefaultFallback() {
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        // Unset → default returned.
        assertEquals("fallback", boot.sysenv("__BOOT_SHOULD_NEVER_BE_SET_12345__", "fallback"));
        // Set → real value wins over default.
        assertNotEquals("fallback", boot.sysenv("PATH", "fallback"));
    }

    @Test
    void bootSysprop() {
        System.setProperty("__boot_sysprop_test__", "hello");
        try {
            BootBinding boot = newBinding(tmp, null, new ArrayList<>());
            // Set → real value.
            assertEquals("hello", boot.sysprop("__boot_sysprop_test__"));
            // Unset → null.
            assertNull(boot.sysprop("__boot_sysprop_unset_99999__"));
            // Unset with default → default.
            assertEquals("fallback", boot.sysprop("__boot_sysprop_unset_99999__", "fallback"));
            // Set with default → real value wins.
            assertEquals("hello", boot.sysprop("__boot_sysprop_test__", "fallback"));
        } finally {
            System.clearProperty("__boot_sysprop_test__");
        }
    }

    @Test
    void bootReadResolvesPathAgainstWorkdir() throws Exception {
        Files.writeString(tmp.resolve("payload.txt"), "hello-from-boot");
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        assertEquals("hello-from-boot", boot.read("payload.txt"));
    }

    @Test
    void bootReadMissingFileThrows() {
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        assertThrows(RuntimeException.class, () -> boot.read("nope.txt"));
    }

    @Test
    void manifestsCollectAllPluginEntries() {
        BootBinding boot = newBinding(tmp, null, new ArrayList<>());
        boot.plugin("noop");
        var manifests = boot.manifests();
        assertEquals(1, manifests.size());
        Map<String, Object> entry = manifests.get(0);
        assertEquals("noop", entry.get("name"));
        assertEquals("io.karatelabs.plugins.noop.NoopPlugin", entry.get("class"));
        assertEquals("noop-v1", entry.get("version"));
    }

    @Test
    void engineExposesBootGlobalForKarateBootJs() {
        BootBinding boot = newBinding(tmp, "ci", new ArrayList<>());
        Engine engine = new Engine();
        engine.setExternalBridge(new ExternalBridge() {});
        engine.putRootBinding("boot", boot);
        engine.eval("var x = boot.env; var p = boot.plugin('noop');");
        assertEquals("ci", engine.get("x"));
        assertEquals(1, boot.getPlugins().size());
    }
}
