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

import java.util.Collections;
import java.util.Map;

/**
 * Karate plugin SPI — singletons-per-Suite that observe the run via {@link RunListener}.
 *
 * <p>Plugins are loaded from a {@code karate-boot.js} file at the workdir root, once per
 * Suite, by {@link BootLoader}. Activation example:</p>
 *
 * <pre>
 * // karate-boot.js
 * const agent = boot.plugin('agent');
 * agent.url = 'http://localhost:4444';
 * agent.params = { dev: true };
 *
 * const openapi = boot.plugin('openapi');
 * openapi.path = 'api/openapi.yaml';
 * openapi.excludes = ['/health/**'];
 * </pre>
 *
 * <p>The {@code boot.plugin('foo')} call resolves {@code foo} to
 * {@code io.karatelabs.plugins.foo.FooPlugin} by name convention, instantiates the class,
 * invokes {@link #onBoot(Suite)} immediately, registers the plugin as a listener on the
 * Suite, and returns the instance so the boot script can configure it.</p>
 *
 * <p>Plugins coexist with the per-call {@link RunListener#onEvent(RunEvent) channel}
 * pattern ({@code karate.channel(...)}) — channels are per-call; plugins are
 * singleton-per-Suite. See AGENT_KARATE.md K43 for the design rationale.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@code karate-boot.js} evaluates → each {@code boot.plugin('name')} call
 *       constructs the plugin and fires {@link #onBoot(Suite)}.</li>
 *   <li>Suite registers the plugin as a {@link RunListener}; it sees every event.</li>
 *   <li>{@code SUITE_ENTER.data.plugins[]} carries each plugin's {@link #getManifest()}.</li>
 *   <li>After {@code SUITE_EXIT}, {@link #onShutdown()} fires.</li>
 * </ol>
 *
 * <p>Failure mode: exceptions during {@code onBoot} fail the Suite loudly. Exceptions
 * inside {@link #onEvent} are logged WARN and dropped — the run continues, that one
 * signal is lost.</p>
 */
public interface Plugin extends RunListener {

    /**
     * Called once per Suite when the {@code boot.plugin('name')} expression evaluates
     * in {@code karate-boot.js}. Throws from here fail the Suite.
     *
     * <p>Default no-op so plugins that only need to observe events ({@code onEvent})
     * can stay terse.</p>
     */
    default void onBoot(Suite suite) {
        // no-op
    }

    /**
     * Called after {@code SUITE_EXIT} fires. Use to release file handles, flush
     * buffers, etc. Exceptions are logged and dropped.
     */
    default void onShutdown() {
        // no-op
    }

    /**
     * Manifest entry recorded under {@code SUITE_ENTER.data.plugins[]} so receivers
     * (e.g. the karate-agent dashboard) know which plugins were active for this run
     * and with what config. Returned map is serialised to JSON verbatim — keep keys
     * primitive ({@code String}, {@code Number}, {@code Boolean}, nested maps).
     *
     * <p>Standard entries: {@code name}, {@code version}. Plugin-specific summary
     * fields are flat under the top-level map. Default returns an empty map; plugins
     * with meaningful manifests should override.</p>
     */
    default Map<String, Object> getManifest() {
        return Collections.emptyMap();
    }

    /**
     * Default {@link RunListener#onEvent(RunEvent)} returns true (continue execution).
     * Plugins that only need lifecycle hooks (onBoot / onShutdown / manifest) without
     * observing events can rely on this and skip the override.
     */
    @Override
    default boolean onEvent(RunEvent event) {
        return true;
    }

}
