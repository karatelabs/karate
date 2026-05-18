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

import io.karatelabs.common.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Backing object for the {@code boot} global exposed inside {@code karate-boot.js}.
 *
 * <p>karate-boot.js is plugin-scripting only — its JS scope is discarded after the
 * file evaluates, so variables defined there cannot leak into the feature-runtime
 * scope. The only API surface is this class. See AGENT_KARATE.md K43.</p>
 *
 * <ul>
 *   <li>{@code boot.env} — the value of {@code karate.env} (CLI {@code -e} flag).</li>
 *   <li>{@code boot.sysenv(name)} — read an OS environment variable.</li>
 *   <li>{@code boot.read(path)} — read a file (relative paths resolve against workdir).</li>
 *   <li>{@code boot.log(msg)} — INFO log line, prefixed {@code [boot]}.</li>
 *   <li>{@code boot.plugin(name)} — resolve + construct the named plugin via the
 *       {@code io.karatelabs.plugins.<name>.<Name>Plugin} convention, fire its
 *       {@link Plugin#onBoot(Suite)}, register it with the Suite, and return the
 *       instance so the boot script can configure it.</li>
 * </ul>
 */
public class BootBinding {

    private static final Logger logger = LoggerFactory.getLogger(BootBinding.class);

    private final Suite suite;
    private final java.nio.file.Path workingDir;
    private final String env;
    private final java.util.function.Consumer<Plugin> registrar;
    private final Set<Plugin> plugins = new LinkedHashSet<>();

    /** Production constructor — wires a live Suite for plugin registration. */
    public BootBinding(Suite suite, String env) {
        this(suite, suite == null ? null : suite.getWorkingDir(), env,
                suite == null ? p -> {} : suite::registerPluginListener);
    }

    /**
     * Explicit constructor for tests + advanced callers — decouples workingDir +
     * listener registration from a fully-instantiated Suite.
     */
    public BootBinding(Suite suite,
                       java.nio.file.Path workingDir,
                       String env,
                       java.util.function.Consumer<Plugin> registrar) {
        this.suite = suite;
        this.workingDir = workingDir;
        this.env = env;
        this.registrar = registrar == null ? p -> {} : registrar;
    }

    /** {@code boot.env} — JS sees this as a property, not a method. */
    public String getEnv() {
        return env;
    }

    /** {@code boot.sysenv('NAME')} — read an OS env var; null if unset. */
    public String sysenv(String name) {
        if (name == null) return null;
        return System.getenv(name);
    }

    /** {@code boot.read('path')} — read text file relative to the Suite's workdir. */
    public String read(String path) {
        if (path == null) throw new IllegalArgumentException("boot.read: path is null");
        try {
            Resource r = Resource.path(path);
            if (r.exists()) {
                return r.toString();
            }
        } catch (Exception e) {
            // try workdir fallback below
        }
        try {
            java.nio.file.Path absolute = workingDir == null
                    ? java.nio.file.Paths.get(path)
                    : workingDir.resolve(path);
            if (java.nio.file.Files.exists(absolute)) {
                return new String(java.nio.file.Files.readAllBytes(absolute), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // fall through to the throw below
        }
        throw new RuntimeException("boot.read: file not found: " + path);
    }

    /** {@code boot.log('...')} — INFO log with [boot] prefix. */
    public void log(Object msg) {
        logger.info("[boot] {}", msg == null ? "null" : msg.toString());
    }

    /**
     * {@code boot.plugin('name')} — resolve + construct + register a plugin.
     *
     * <p>Resolution by name convention: {@code 'openapi'} →
     * {@code io.karatelabs.plugins.openapi.OpenapiPlugin}. The class is loaded from
     * the runtime classloader; missing plugin → boot-time failure (suite fails loud).</p>
     *
     * <p>Same name twice returns the same singleton instance.</p>
     */
    public Plugin plugin(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("boot.plugin: name is null or empty");
        }
        // Singleton-per-name within this BootBinding (which is itself per-Suite).
        for (Plugin existing : plugins) {
            if (name.equals(pluginShortName(existing))) {
                return existing;
            }
        }
        String className = "io.karatelabs.plugins." + name + "." + capitalize(name) + "Plugin";
        Plugin plugin;
        try {
            Class<?> cls = Class.forName(className);
            Object instance = cls.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Plugin)) {
                throw new RuntimeException(
                        "boot.plugin('" + name + "'): " + className
                                + " does not implement io.karatelabs.core.Plugin");
            }
            plugin = (Plugin) instance;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "boot.plugin('" + name + "'): not on classpath. Expected "
                            + className + " (name-convention resolution).", e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "boot.plugin('" + name + "'): failed to construct " + className
                            + " — " + e.getMessage(), e);
        }
        // Fire onBoot eagerly per K43. Throws here fail the Suite.
        plugin.onBoot(suite);
        plugins.add(plugin);
        registrar.accept(plugin);
        logger.info("plugin booted: {} ({})", name, plugin.getClass().getName());
        return plugin;
    }

    public Set<Plugin> getPlugins() {
        return plugins;
    }

    /** Collected manifest array shape for SUITE_ENTER.data.plugins[]. */
    public java.util.List<Map<String, Object>> manifests() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Plugin p : plugins) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", pluginShortName(p));
            entry.put("class", p.getClass().getName());
            Map<String, Object> manifest = p.getManifest();
            if (manifest != null) entry.putAll(manifest);
            out.add(entry);
        }
        return out;
    }

    private static String pluginShortName(Plugin p) {
        // io.karatelabs.plugins.openapi.OpenapiPlugin → "openapi"
        String pkg = p.getClass().getPackage().getName();
        int lastDot = pkg.lastIndexOf('.');
        return lastDot < 0 ? pkg : pkg.substring(lastDot + 1);
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
