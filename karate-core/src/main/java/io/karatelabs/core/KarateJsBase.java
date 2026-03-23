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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    KarateJsLog logFacade; // lazy-initialized

    KarateJsBase(Resource root, HttpClient client) {
        this.root = root;
        this.client = client;
        http = new HttpRequestBuilder(client);
        this.engine = new Engine();
        engine.setOnConsoleLog(s -> LogContext.get().log(s));
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
        String errorMessage = rt != null && rt.getError() != null ? rt.getError().getMessage() : null;
        info.put("errorMessage", errorMessage);
        return info;
    }

    /**
     * Returns scenario data map for karate.scenario.
     * V1 compatible fields: name, sectionIndex, exampleIndex, exampleData, line, description
     */
    Map<String, Object> getScenarioData() {
        Scenario s = getScenario();
        if (s == null) return Map.of();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", s.getName());
        data.put("description", s.getDescription());
        data.put("line", s.getLine());
        data.put("sectionIndex", s.getSection().getIndex());
        data.put("exampleIndex", s.getExampleIndex());
        Map<String, Object> exampleData = s.getExampleData();
        if (exampleData != null) {
            data.put("exampleData", exampleData);
        }
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
        // Feature-level tags first
        List<Tag> featureTags = s.getFeature().getTags();
        if (featureTags != null) {
            for (Tag tag : featureTags) {
                result.put(tag.getName(), tag.getValues());
            }
        }
        // Scenario-level tags (override feature-level)
        List<Tag> scenarioTags = s.getTags();
        if (scenarioTags != null) {
            for (Tag tag : scenarioTags) {
                result.put(tag.getName(), tag.getValues());
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
            LogContext.get().log(sb.toString());
            return null;
        };
    }

    /**
     * Embed content in the report. Auto-detects MIME type if not provided.
     */
    JavaInvokable embed() {
        return args -> {
            if (args.length < 1) {
                throw new RuntimeException("embed() needs at least one argument: data");
            }
            Object dataArg = args[0];
            String mimeType = args.length > 1 && args[1] != null
                    ? args[1].toString() : KarateJsUtils.detectMimeType(dataArg);
            String name = args.length > 2 ? args[2].toString() : null;

            byte[] data = KarateJsUtils.convertToBytes(dataArg);
            LogContext.get().embed(data, mimeType, name);
            return null;
        };
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
