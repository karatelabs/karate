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
package io.karatelabs.core;

import io.karatelabs.common.Resource;
import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.http.HttpClient;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.js.*;
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.ResourceResolver;
import io.karatelabs.match.Result;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class holding shared state and infrastructure for the karate.* API.
 * <p>
 * This abstract class provides:
 * - Core state fields (engine, client, context, handlers)
 * - Runtime context access via {@link KarateJsContext}
 * - Initialization of the JavaScript engine and built-in functions
 * - Helper methods for deriving scenario/feature data from context
 * <p>
 * Subclasses (like {@link KarateJs}) implement the actual API methods
 * that use this infrastructure.
 *
 * @see KarateJs for the main API implementation
 * @see KarateJsUtils for stateless utility methods
 * @see KarateJsContext for runtime context interface
 */
abstract class KarateJsBase implements SimpleObject {

    static final Logger logger = LogContext.RUNTIME_LOGGER;

    public final Resource root;
    public final Engine engine;
    public final HttpClient client;
    public final HttpRequestBuilder http;

    ResourceResolver resourceResolver;
    Markup _markup;
    Consumer<String> onDoc;
    BiConsumer<Context, Result> onMatch;
    KarateJsContext context; // provides access to ScenarioRuntime, Config, WorkingDir
    String env;
    String overrideOutputDir; // for tests to override output directory
    MockHandler mockHandler; // non-null only in mock context
    io.karatelabs.http.HttpRequest prevRequest; // tracks previous HTTP request
    io.karatelabs.http.HttpResponse prevResponse; // tracks previous HTTP response (for karate.response)
    KarateJsLog logFacade; // lazy-initialized

    KarateJsBase(Resource root, HttpClient client) {
        this.root = root;
        this.client = client;
        http = new HttpRequestBuilder(client);
        this.engine = new Engine();
        engine.setOnConsoleLog(s -> SCENARIO_LOG.info(s));
        // TODO: implement whitelisting for safety - currently allows access to all Java classes
        engine.setExternalBridge(new io.karatelabs.js.ExternalBridge() {
        });
        // Note: engine.put() for karate, read, match is done in KarateJs constructor
    }

    public void setOnDoc(Consumer<String> onDoc) {
        this.onDoc = onDoc;
    }

    public void setResourceResolver(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public void setOnMatch(BiConsumer<Context, Result> onMatch) {
        this.onMatch = onMatch;
    }

    public void setContext(KarateJsContext context) {
        this.context = context;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public void setMockHandler(MockHandler handler) {
        this.mockHandler = handler;
    }

    public void setOutputDir(String outputDir) {
        this.overrideOutputDir = outputDir;
    }

    // ========== Helper Methods for Context Access ==========

    ScenarioRuntime getRuntime() {
        return context != null ? context.getRuntime() : null;
    }

    Scenario getScenario() {
        ScenarioRuntime rt = getRuntime();
        return rt != null ? rt.getScenario() : null;
    }

    Resource getCurrentResource() {
        ScenarioRuntime rt = getRuntime();
        return rt != null && rt.getFeatureRuntime() != null
                ? rt.getFeatureRuntime().getFeature().getResource()
                : root;
    }

    String getOutputDir() {
        if (overrideOutputDir != null) {
            return overrideOutputDir;
        }
        ScenarioRuntime rt = getRuntime();
        if (rt != null && rt.getFeatureRuntime() != null && rt.getFeatureRuntime().getSuite() != null) {
            return rt.getFeatureRuntime().getSuite().getOutputDir();
        }
        return "target";  // Default fallback
    }

    // ========== Scenario Lifecycle Methods ==========
    // These methods derive data from the Scenario/ScenarioRuntime via context.

    /**
     * Returns scenario info map for karate.info.
     * Contains: scenarioName, scenarioDescription, featureDir, featureFileName, errorMessage
     */
    Map<String, Object> getInfo() {
        Scenario s = getScenario();
        if (s == null) return Map.of();
        Map<String, Object> info = new LinkedHashMap<>();
        Resource featureResource = s.getFeature().getResource();
        if (featureResource.isFile() && featureResource.getPath() != null) {
            info.put("featureDir", featureResource.getPath().getParent().toString());
            info.put("featureFileName", featureResource.getPath().getFileName().toString());
        }
        info.put("scenarioName", s.getName());
        info.put("scenarioDescription", s.getDescription());
        ScenarioRuntime rt = getRuntime();
        if (rt != null && rt.getError() != null) {
            info.put("errorMessage", rt.getError().getMessage());
        }
        return info;
    }

    /**
     * Returns scenario data map for karate.scenario.
     * V1 compatible fields: name, sectionIndex, exampleIndex, exampleData, line, description.
     * Adds {@code slug} — the stable identity this scenario reports under: an
     * author-set {@code __id} (once bound) wins, else the derived feature-path +
     * name. Mirrors the JSONL {@code slug}, so a running test can introspect the
     * same id receivers will key on.
     */
    Map<String, Object> getScenarioData() {
        Scenario s = getScenario();
        if (s == null) return Map.of();
        ScenarioRuntime rt = getRuntime();
        String stableId = rt != null ? rt.resolveStableId() : null;
        // One identity routine shared with ScenarioResult.toJson (RunUtils) so the
        // JS API and the JSONL/report payload never drift — same name, slug, line,
        // indices, exampleData.
        return RunUtils.scenarioIdentity(s, stableId);
    }

    /**
     * Returns suite data map for karate.suite. Fields: {@code threadCount}, {@code dryRun}.
     * The {@code karate.env} shortcut predates this map and stays where it is; everything
     * else suite-level should land here so future suite introspection has a single home.
     * Returns an empty map when no Suite is in scope (e.g. unit tests that drive KarateJs
     * without a runtime).
     */
    Map<String, Object> getSuiteData() {
        ScenarioRuntime rt = getRuntime();
        if (rt == null || rt.getFeatureRuntime() == null) return Map.of();
        io.karatelabs.core.Suite suite = rt.getFeatureRuntime().getSuite();
        if (suite == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threadCount", suite.threadCount);
        data.put("dryRun", suite.dryRun);
        return data;
    }

    /**
     * Returns feature data map for karate.feature.
     * V1 compatible fields: name, description, prefixedPath, fileName, parentDir
     */
    Map<String, Object> getFeatureData() {
        Scenario s = getScenario();
        if (s == null) return Map.of();
        Feature feature = s.getFeature();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", feature.getName());
        data.put("description", feature.getDescription());
        Resource resource = feature.getResource();
        data.put("prefixedPath", resource.getPrefixedPath());
        if (resource.isFile() && resource.getPath() != null) {
            data.put("fileName", resource.getPath().getFileName().toString());
            data.put("parentDir", resource.getPath().getParent().toString());
        }
        return data;
    }

    /**
     * Returns the effective tags (scenario + feature) as a List of tag names.
     * V1 compatible: returns tag text without '@' prefix.
     */
    List<String> getTags() {
        Scenario s = getScenario();
        if (s == null) return List.of();
        List<String> result = new ArrayList<>();
        // Feature-level tags first
        List<Tag> featureTags = s.getFeature().getTags();
        if (featureTags != null) {
            for (Tag tag : featureTags) {
                result.add(tag.getText());
            }
        }
        // Scenario-level tags
        List<Tag> scenarioTags = s.getTags();
        if (scenarioTags != null) {
            for (Tag tag : scenarioTags) {
                if (!result.contains(tag.getText())) {
                    result.add(tag.getText());
                }
            }
        }
        return result;
    }

    /**
     * Returns tag values map for karate.tagValues.
     * V1 compatible: Map<String, List<String>> where key is tag name, value is list of values.
     */
    Map<String, List<String>> getTagValues() {
        Scenario s = getScenario();
        if (s == null) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        // Feature-level tags: aggregate same-name tags (e.g. @suite=x + @suite=y -> [x, y])
        List<Tag> featureTags = s.getFeature().getTags();
        if (featureTags != null) {
            for (Tag tag : featureTags) {
                result.computeIfAbsent(tag.getName(), k -> new ArrayList<>()).addAll(tag.getValues());
            }
        }
        // Scenario-level tags: first occurrence of a name replaces the feature-level entry,
        // subsequent same-name tags at scenario level merge with that replacement.
        List<Tag> scenarioTags = s.getTags();
        if (scenarioTags != null) {
            Set<String> seenAtScenarioLevel = new HashSet<>();
            for (Tag tag : scenarioTags) {
                String name = tag.getName();
                if (seenAtScenarioLevel.add(name)) {
                    result.put(name, new ArrayList<>(tag.getValues()));
                } else {
                    result.get(name).addAll(tag.getValues());
                }
            }
        }
        return result;
    }

    /**
     * Returns scenario outline data for karate.scenarioOutline.
     * Returns null if not in a scenario outline.
     */
    Map<String, Object> getScenarioOutlineData() {
        Scenario s = getScenario();
        if (s == null || !s.isOutlineExample()) {
            return null;  // V1 returns null when not in an outline
        }
        FeatureSection section = s.getSection();
        if (!section.isOutline()) {
            return null;
        }
        ScenarioOutline outline = section.getScenarioOutline();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", outline.getName());
        data.put("description", outline.getDescription());
        data.put("line", outline.getLine());
        data.put("sectionIndex", section.getIndex());
        data.put("exampleTableCount", outline.getNumExampleTables());
        data.put("exampleTables", outline.getAllExampleData());
        data.put("numScenariosToExecute", outline.getNumScenarios());
        return data;
    }

    /**
     * Returns the config settings (from configure keyword) as a KarateConfig.
     * Returns a copy to prevent mutation from JavaScript.
     */
    KarateConfig getConfig() {
        if (context != null) {
            KarateConfig cfg = context.getConfig();
            if (cfg != null) {
                return cfg.copy();
            }
        }
        return new KarateConfig();
    }

    JavaInvokable abort() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt != null) {
                rt.abort();
            }
            return null;
        };
    }

    JavaInvokable setup() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt == null) {
                throw new RuntimeException("karate.setup() is not available in this context");
            }
            String name = args.length > 0 && args[0] != null ? args[0].toString() : null;
            return rt.executeSetup(name);
        };
    }

    JavaInvokable setupOnce() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt == null) {
                throw new RuntimeException("karate.setupOnce() is not available in this context");
            }
            String name = args.length > 0 && args[0] != null ? args[0].toString() : null;
            return rt.executeSetupOnce(name);
        };
    }

    /**
     * karate.callonce() - Execute a feature file once per feature and cache the result.
     */
    JavaInvokable callonce() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt == null) {
                throw new RuntimeException("karate.callonce() is not available in this context");
            }
            if (args.length == 0) {
                throw new RuntimeException("karate.callonce() requires at least one argument (feature path)");
            }
            String path = args[0].toString();
            Object arg = args.length > 1 ? args[1] : null;
            return rt.executeJsCallOnce(path, arg);
        };
    }

    /**
     * karate.callSingle() - Execute a feature/JS file once per Suite and cache the result.
     */
    JavaInvokable callSingle() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt == null) {
                throw new RuntimeException("karate.callSingle() is not available in this context");
            }
            if (args.length == 0) {
                throw new RuntimeException("karate.callSingle() requires at least one argument (path)");
            }
            String path = args[0].toString();
            Object arg = args.length > 1 ? args[1] : null;
            return rt.executeCallSingle(path, arg);
        };
    }

    /**
     * karate.configure() - Apply configuration from JavaScript.
     */
    JavaInvokable configure() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("configure() needs two arguments: key and value");
            }
            String key = args[0].toString();
            Object value = args[1];
            ScenarioRuntime rt = getRuntime();
            if (rt != null) {
                rt.configure(key, value);
            }
            return null;
        };
    }

    /**
     * Returns system properties available via karate.properties['key'].
     */
    Map<String, String> getProperties() {
        ScenarioRuntime rt = getRuntime();
        if (rt != null && rt.getFeatureRuntime() != null && rt.getFeatureRuntime().getSuite() != null) {
            return rt.getFeatureRuntime().getSuite().getSystemProperties();
        }
        Map<String, String> props = new LinkedHashMap<>();
        System.getProperties().forEach((k, v) -> props.put(k.toString(), v.toString()));
        return props;
    }

    private static final LogContext.LogWriter SCENARIO_LOG = LogContext.with(LogContext.SCENARIO_LOGGER);

    @SuppressWarnings("unchecked")
    JavaInvokable log() {
        return args -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(" ");
                Object arg = args[i];
                if (arg instanceof Node) {
                    sb.append(Xml.toString((Node) arg, true));
                } else if (arg instanceof Map || arg instanceof List) {
                    sb.append(StringUtils.formatJson(arg));
                } else {
                    sb.append(arg);
                }
            }
            SCENARIO_LOG.info(sb.toString());
            return null;
        };
    }

    /**
     * Embed content in the report. Two forms:
     * <ul>
     *   <li><b>Single-part (legacy):</b> {@code embed(data, mime?, name?)} — auto-detects MIME
     *       when omitted.</li>
     *   <li><b>Multi-part (object):</b> {@code embed({ name, parts:[{role, mime?, data|path|url}], meta })}
     *       — for rich embeds (e.g. image-comparison). Dispatched when the first arg is a Map
     *       carrying a {@code parts} list. Each part: {@code role} is required;
     *       {@code data} (bytes / Uint8Array) or {@code path} (a resource string core reads to
     *       bytes) or {@code url} (a report-relative asset the caller wrote); {@code mime} is
     *       auto-detected from the bytes when omitted.</li>
     * </ul>
     */
    JavaInvokable embed() {
        return args -> {
            if (args.length < 1) {
                throw new RuntimeException("embed() needs at least one argument: data");
            }
            Object first = unwrapJs(args[0]);
            if (first instanceof Map<?, ?> map && map.get("parts") instanceof List) {
                LogContext.get().embed(toMultiPartEmbed(map));
                return null;
            }
            // single-part (legacy)
            String mimeType = args.length > 1 && args[1] != null
                    ? args[1].toString() : KarateJsUtils.detectMimeType(first);
            String name = args.length > 2 ? args[2].toString() : null;
            byte[] data = KarateJsUtils.convertToBytes(first);
            LogContext.get().embed(data, mimeType, name);
            return null;
        };
    }

    private StepResult.Embed toMultiPartEmbed(Map<?, ?> map) {
        String name = map.get("name") != null ? map.get("name").toString() : null;
        List<StepResult.Part> parts = new ArrayList<>();
        for (Object partObj : (List<?>) map.get("parts")) {
            Object pu = unwrapJs(partObj);
            if (!(pu instanceof Map<?, ?> pm)) {
                throw new RuntimeException("embed(): each 'parts' entry must be an object");
            }
            Object roleObj = pm.get("role");
            if (roleObj == null) {
                throw new RuntimeException("embed(): each part needs a 'role'");
            }
            String role = roleObj.toString();
            String mime = pm.get("mime") != null ? pm.get("mime").toString() : null;
            Object url = pm.get("url");
            if (url != null) {
                parts.add(new StepResult.Part(role, mime, url.toString()));
                continue;
            }
            byte[] bytes;
            Object dataObj = pm.get("data");
            Object pathObj = pm.get("path");
            if (dataObj != null) {
                bytes = KarateJsUtils.convertToBytes(unwrapJs(dataObj));
            } else if (pathObj != null) {
                bytes = readPathBytes(pathObj.toString());
            } else {
                throw new RuntimeException("embed(): part '" + role + "' needs 'data', 'path', or 'url'");
            }
            parts.add(new StepResult.Part(role, mime != null ? mime : KarateJsUtils.detectMimeType(bytes), bytes));
        }
        Object meta = map.get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaMap = meta instanceof Map ? (Map<String, Object>) meta : null;
        return new StepResult.Embed(name, parts, metaMap);
    }

    /** Read an embed part's {@code path} (this:/classpath:/file:/relative) into bytes. */
    private byte[] readPathBytes(String path) {
        try (java.io.InputStream is = getCurrentResource().resolve(path).getStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("embed(): failed to read part path '" + path + "': " + e.getMessage(), e);
        }
    }

    /** Unwrap a JsValue (e.g. a nested Uint8Array / object) to its idiomatic Java value. */
    private static Object unwrapJs(Object o) {
        return o instanceof JsValue jv ? jv.getJavaValue() : o;
    }

    /**
     * karate.signal() - Signal a result for listen/listenResult.
     */
    JavaInvokable signal() {
        return args -> {
            ScenarioRuntime rt = getRuntime();
            if (rt != null && args.length > 0) {
                rt.setListenResult(args[0]);
            }
            return null;
        };
    }

    KarateJsLog getLogger() {
        if (logFacade == null) {
            logFacade = new KarateJsLog();
        }
        return logFacade;
    }

    /**
     * karate.prevRequest - Returns the previous HTTP request made in this scenario.
     * V1 compatibility: body is raw byte[] (not parsed)
     */
    Map<String, Object> getPrevRequest() {
        if (prevRequest == null) {
            return null;
        }
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("method", prevRequest.getMethod());
        map.put("url", prevRequest.getUrlAndPath());
        map.put("headers", prevRequest.getHeaders());
        map.put("body", prevRequest.getBody());
        return map;
    }

}
