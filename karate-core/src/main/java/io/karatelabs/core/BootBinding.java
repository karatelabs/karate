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
 * <p>karate-boot.js is ext-scripting only — its JS scope is discarded after the
 * file evaluates, so variables defined there cannot leak into the feature-runtime
 * scope. The only API surface is this class. See AGENT_KARATE.md K43.</p>
 *
 * <ul>
 *   <li>{@code boot.env} — the value of {@code karate.env} (CLI {@code -e} flag).</li>
 *   <li>{@code boot.sysenv(name)} / {@code boot.sysenv(name, default)} — read an
 *       OS environment variable, optionally falling back to a default when unset
 *       or empty.</li>
 *   <li>{@code boot.sysprop(name)} / {@code boot.sysprop(name, default)} — read a
 *       JVM system property; reads from the Suite's merged property map when
 *       available.</li>
 *   <li>{@code boot.classpath(dir)} — declare the resource root: where a {@code classpath:}
 *       miss falls back to, and (Runner lane) what the working dir re-anchors on.</li>
 *   <li>{@code boot.read(path)} — read a file on the unified reference rule (leading {@code /}
 *       anchors the project root).</li>
 *   <li>{@code boot.log(msg)} — INFO log line, prefixed {@code [boot]}.</li>
 *   <li>{@code boot.ext(name)} — resolve + construct the named ext via the
 *       {@code io.karatelabs.ext.<name>.<Name>Ext} convention, fire its
 *       {@link Ext#onBoot(Suite)}, register it with the Suite, and return the
 *       instance so the boot script can configure it.</li>
 * </ul>
 */
public class BootBinding {

    private static final Logger logger = LoggerFactory.getLogger(BootBinding.class);

    private final Suite suite;
    private final java.nio.file.Path root;
    private final String env;
    private final java.util.function.Consumer<Ext> registrar;
    private final Set<Ext> exts = new LinkedHashSet<>();
    private String classpathDir;

    /**
     * Constructor for tests + advanced callers — decouples the root + listener
     * registration from a fully-instantiated Suite (the production caller,
     * {@link BootLoader#evalIfPresent}, runs while the Suite is still being built).
     *
     * @param root THE project root every {@code boot.*} reference resolves against
     */
    public BootBinding(Suite suite,
                       java.nio.file.Path root,
                       String env,
                       java.util.function.Consumer<Ext> registrar) {
        this.suite = suite;
        this.root = root;
        this.env = env;
        this.registrar = registrar == null ? e -> {} : registrar;
    }

    /** {@code boot.env} — JS sees this as a property, not a method. */
    public String getEnv() {
        return env;
    }

    /** {@code boot.sysenv('NAME')} — read an OS env var; null if unset. */
    public String sysenv(String name) {
        return sysenv(name, null);
    }

    /**
     * {@code boot.sysenv('NAME', 'default')} — read an OS env var, falling back
     * to {@code defaultValue} when unset or empty (shell {@code ${VAR:-default}}
     * semantics). Collapses the previous {@code boot.sysenv('FOO') || 'default'}
     * idiom into a single call.
     */
    public String sysenv(String name, String defaultValue) {
        if (name == null) return defaultValue;
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /** {@code boot.sysprop('NAME')} — read a JVM system property; null if unset. */
    public String sysprop(String name) {
        return sysprop(name, null);
    }

    /**
     * {@code boot.sysprop('NAME', 'default')} — read a JVM system property,
     * falling back to {@code defaultValue} when unset or empty. Reads from the
     * Suite's merged property map when available so {@code Runner.Builder.systemProperties(...)}
     * injections are visible, otherwise falls back to {@link System#getProperty(String)}.
     */
    public String sysprop(String name, String defaultValue) {
        if (name == null) return defaultValue;
        String value;
        if (suite != null) {
            Map<String, String> props = suite.getSystemProperties();
            value = props == null ? null : props.get(name);
        } else {
            value = System.getProperty(name);
        }
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * {@code boot.classpath('src/test/resources')} — <b>declare the resource root</b>: the directory
     * a {@code classpath:} reference retries against when the classloader misses, and (in a Java
     * Runner-lane run that did not set an explicit working dir) the directory the working dir
     * re-anchors to. One committed line makes a Maven/Gradle project resolve its references
     * identically under a JVM run — where the classloader hits and this mapping stays inert — and
     * under a bare-folder serve run, where the fallback carries {@code classpath:} refs to the
     * declared dir.
     *
     * <p>The argument is a <b>root-relative reference</b>, so {@code file:} / {@code classpath:} /
     * {@code this:} and Windows drive-letter absolutes are rejected. {@code ''} is valid and means
     * the root itself (the "unify only, map nothing" spelling) — which is also what an undeclared
     * project gets, so a bare-folder project never needs this call. Last call wins.</p>
     */
    public void classpath(String dir) {
        if (dir == null) {
            throw new IllegalArgumentException("boot.classpath: dir is null — pass a project-relative "
                    + "directory such as 'src/test/resources', or '' for the project root");
        }
        if (dir.startsWith(Resource.FILE_COLON) || dir.startsWith(Resource.CLASSPATH_COLON)
                || dir.startsWith(Resource.THIS_COLON)
                || (!dir.startsWith("/") && java.nio.file.Path.of(dir).isAbsolute())) {
            throw new IllegalArgumentException("boot.classpath('" + dir + "'): expected a directory "
                    + "RELATIVE to the project root (e.g. 'src/test/resources'), not an absolute or "
                    + "prefixed reference");
        }
        this.classpathDir = Resource.stripLeadingSlashes(dir);
    }

    /**
     * The directory declared by {@link #classpath(String)}, root-relative and de-slashed, or
     * {@code null} when the project declared nothing (then the fallback dir IS the root).
     */
    public String getClasspathDir() {
        return classpathDir;
    }

    /**
     * {@code boot.read('path')} — read a text file, on the one unified rule: a leading {@code /}
     * anchors THE project root, a bare ref is root-relative, {@code classpath:} is
     * classloader-first then the {@link #classpath(String)} fallback, and {@code file:} is a host
     * path. Declare {@code boot.classpath(...)} before reading a {@code classpath:} ref that is
     * not on the real classpath.
     */
    public String read(String path) {
        if (path == null) throw new IllegalArgumentException("boot.read: path is null");
        java.nio.file.Path classpathRoot = classpathDir == null || root == null
                ? root : root.resolve(classpathDir).normalize();
        try {
            Resource r = Resource.path(path, root, classpathRoot);
            if (r.exists()) {
                return r.getText();
            }
            throw new RuntimeException("boot.read: file not found: " + path
                    + " (resolved to " + r + "; root is " + root
                    + " — a leading '/' anchors the project root, 'file:' is a host path)");
        } catch (io.karatelabs.common.ResourceNotFoundException e) {
            throw new RuntimeException("boot.read: file not found: " + path
                    + " (root is " + root + " — a leading '/' anchors the project root, "
                    + "'file:' is a host path; declare boot.classpath(dir) to map 'classpath:' refs)", e);
        }
    }

    /** {@code boot.log('...')} — INFO log with [boot] prefix. */
    public void log(Object msg) {
        logger.info("[boot] {}", msg == null ? "null" : msg.toString());
    }

    /**
     * {@code boot.ext('name')} — resolve + construct + register an ext.
     *
     * <p>Resolution by name convention: {@code 'openapi'} →
     * {@code io.karatelabs.ext.openapi.OpenapiExt}. The class is loaded from
     * the runtime classloader; missing ext → boot-time failure (suite fails loud).</p>
     *
     * <p>Same name twice returns the same singleton instance.</p>
     */
    public Ext ext(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("boot.ext: name is null or empty");
        }
        // Singleton-per-name within this BootBinding (which is itself per-Suite).
        for (Ext existing : exts) {
            if (name.equals(extShortName(existing))) {
                return existing;
            }
        }
        String className = extClassName(name);
        Ext ext;
        try {
            Class<?> cls = Class.forName(className);
            Object instance = cls.getDeclaredConstructor().newInstance();
            if (!(instance instanceof Ext)) {
                throw new RuntimeException(
                        "boot.ext('" + name + "'): " + className
                                + " does not implement io.karatelabs.core.Ext");
            }
            ext = (Ext) instance;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "boot.ext('" + name + "'): not on classpath. Expected "
                            + className + " (name-convention resolution).", e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "boot.ext('" + name + "'): failed to construct " + className
                            + " — " + e.getMessage(), e);
        }
        // Fire onBoot eagerly per K43. Throws here fail the Suite.
        ext.onBoot(suite);
        exts.add(ext);
        registrar.accept(ext);
        logger.info("ext booted: {} ({})", name, ext.getClass().getName());
        return ext;
    }

    /**
     * {@code boot.has('name')} — is this ext available to boot? A pure classpath probe using the same
     * name convention as {@link #ext(String)}; constructs nothing and registers nothing.
     *
     * <p>Exists because {@code boot.ext} is deliberately strict — a typo, or a missing ext the project
     * genuinely depends on, must fail the suite loudly. But a project may legitimately depend on an ext
     * <i>only when it is present</i>: a kit whose gRPC/Kafka beat is optional still has to run on a
     * runtime that ships without those leaves. Before this, the only way to express that was an external
     * switch (an env var) — which the project cannot see, cannot default correctly, and which nobody
     * driving the console can set, so the whole project would fail to boot with an error naming a class
     * rather than the switch. {@code if (boot.has('grpc')) { … }} lets the project decide for itself.</p>
     *
     * <p>An already-booted ext reports {@code true} without a class lookup.</p>
     */
    public boolean has(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (Ext existing : exts) {
            if (name.equals(extShortName(existing))) {
                return true;
            }
        }
        return extAvailable(name);
    }

    /** The name-convention class of an ext: {@code 'openapi'} → {@code io.karatelabs.ext.openapi.OpenapiExt}. */
    static String extClassName(String name) {
        return "io.karatelabs.ext." + name + "." + capitalize(name) + "Ext";
    }

    /**
     * Is an ext of this name on the classpath? The raw probe behind {@link #has} — constructs nothing,
     * registers nothing. Also how {@link ExtHint} decides whether a missing global is worth naming an
     * ext for, without either place hard-coding the convention twice.
     */
    static boolean extAvailable(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            Class.forName(extClassName(name));
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Set<Ext> getExts() {
        return exts;
    }

    /**
     * The ext globals registered during boot (each ext's {@code onBoot} ran eagerly in {@link #ext}, e.g.
     * RulesExt's {@code Rule}/{@code Schema}/{@code match} instances + a {@code Check} factory) — the
     * seedable scenario globals a caller OUTSIDE a run can apply to its own engine to reach an ext the same
     * way a real run's {@link ScenarioRuntime} does (it seeds this very map). A value is a shared singleton
     * instance, or an {@link ExtGlobalFactory} the caller mints per-context. Empty when no boot file ran.
     */
    public Map<String, Object> getGlobals() {
        return suite == null ? java.util.Collections.emptyMap() : suite.getGlobals();
    }

    /** Collected manifest array shape for SUITE_ENTER.data.exts[]. */
    public java.util.List<Map<String, Object>> manifests() {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Ext e : exts) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", extShortName(e));
            entry.put("class", e.getClass().getName());
            Map<String, Object> manifest = e.getManifest();
            if (manifest != null) entry.putAll(manifest);
            out.add(entry);
        }
        return out;
    }

    private static String extShortName(Ext e) {
        // io.karatelabs.ext.openapi.OpenapiExt → "openapi"
        String pkg = e.getClass().getPackage().getName();
        int lastDot = pkg.lastIndexOf('.');
        return lastDot < 0 ? pkg : pkg.substring(lastDot + 1);
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
