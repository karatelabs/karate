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
package io.karatelabs.driver.e2e;

import io.karatelabs.driver.DriverException;
import io.karatelabs.driver.Element;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for the named init-script registry (addInitScript / removeInitScript): modules run in
 * every new document, survive navigation, install after the built-in runtime and after declared
 * dependencies, and stop re-installing once removed. Driven against a real browser.
 */
class InitScriptE2eTest extends DriverTestBase {

    private final List<String> registered = new ArrayList<>();

    private void register(String name, String source, String... deps) {
        driver.addInitScript(name, source, deps);
        registered.add(name);
    }

    @AfterEach
    void cleanupInitScripts() {
        // the driver is shared across tests — unregister so modules don't leak into the next test
        for (String name : registered) {
            driver.removeInitScript(name);
        }
        registered.clear();
    }

    private static String blank() {
        return "data:text/html,<button id='go'>Run</button>";
    }

    @Test
    void testInjectsIntoCurrentDocumentImmediately() {
        driver.setUrl(blank());
        register("mark", "window.__mark = 'M';");
        // new-document installs fire on the NEXT document, but the call also injects into the open one
        assertEquals("M", driver.script("window.__mark"));
    }

    @Test
    void testSurvivesNavigation() {
        driver.setUrl(blank());
        register("mark", "window.__mark = 'M';");
        driver.setUrl(blank());
        assertEquals("M", driver.script("window.__mark"), "module must re-run on the new document");
    }

    @Test
    void testDependencyRunsBefore() {
        driver.setUrl(blank());
        register("base", "window.__seq = 'A';");
        register("dependent", "window.__seq = (window.__seq || '') + 'B';", "base");
        driver.setUrl(blank());
        // base must execute before dependent on every new document
        assertEquals("AB", driver.script("window.__seq"));
    }

    @Test
    void testIdempotentByName() {
        driver.setUrl(blank());
        register("mark", "window.__mark = 'first';");
        driver.addInitScript("mark", "window.__mark = 'second';"); // already registered → ignored
        driver.setUrl(blank());
        assertEquals("first", driver.script("window.__mark"));
    }

    @Test
    void testUnregisteredDependencyThrows() {
        driver.setUrl(blank());
        assertThrows(DriverException.class,
                () -> driver.addInitScript("needy", "window.__x = 1;", "missing"));
    }

    @Test
    void testRemoveStopsReinstall() {
        driver.setUrl(blank());
        register("mark", "window.__mark = 'M';");
        driver.removeInitScript("mark");
        registered.remove("mark");
        driver.setUrl(blank());
        assertNull(driver.script("window.__mark"), "removed module must not run on later documents");
    }

    /**
     * The built-in runtime is installed before any contributed module, so a module that seeds a
     * partial {@code window.__kjs} (without the wildcard resolver) cannot shadow the runtime — a
     * {@code {tag}text} locator still resolves on a freshly navigated document.
     */
    @Test
    void testRuntimeInstalledBeforeModuleThatSeedsPartialNamespace() {
        driver.setUrl(blank());
        register("partial", "if (!window.__kjs) { window.__kjs = {}; } window.__kjs.ext = {};");
        driver.setUrl(blank());
        Element el = driver.locate("{button}Run");
        assertEquals("go", el.attribute("id"));
    }

}
