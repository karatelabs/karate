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

import io.karatelabs.common.FileUtils;
import io.karatelabs.common.Resource;
import io.karatelabs.common.ResourceType;
import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import io.karatelabs.output.LogContext;
import net.minidev.json.JSONValue;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.js.Engine;
import io.karatelabs.js.JavaInvokable;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.JsLazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import com.jayway.jsonpath.JsonPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Mock server request handler that routes requests to matching scenarios.
 * Implements Function&lt;HttpRequest, HttpResponse&gt; for use with HttpServer.
 *
 * Uses ScenarioRuntime and StepExecutor for proper step execution,
 * ensuring all keywords (def, xml, json, call, etc.) work correctly.
 */
public class MockHandler implements Function<HttpRequest, HttpResponse> {

    private static final Logger logger = LogContext.MOCK_LOGGER;

    private static final String ALLOWED_METHODS = "GET, HEAD, POST, PUT, DELETE, PATCH, OPTIONS";

    /**
     * The disclosure header every karate-fabricated mock response carries: {@code Karate-Mock: true}.
     * It exists so a consumer can distinguish a stand-in from the real system <b>as a fact reported by
     * the responder</b>, not as an inference from its address — a real service on {@code localhost} is
     * ordinary, so nothing about a URL can carry this. Read it that way in both directions: its presence
     * is certain, its absence proves nothing (any non-karate stub answers exactly like a real server).
     * Suppressible via {@code MockConfig#setMockHeaderEnabled(false)}.
     */
    public static final String KARATE_MOCK_HEADER = "Karate-Mock";

    // Current request being processed (protected by requestLock)
    private HttpRequest currentRequest;

    // Set when the current request was served by karate.proceed(): the body came from the REAL upstream,
    // so the response must not claim to be mock-fabricated (protected by requestLock, like currentRequest)
    private boolean proceeded;

    public HttpRequest getCurrentRequest() {
        return currentRequest;
    }

    private final List<Feature> features = new ArrayList<>();
    private final Map<String, Object> globals = new LinkedHashMap<>();
    private final MockConfig config = new MockConfig();
    private final ReentrantLock requestLock = new ReentrantLock();
    private final String pathPrefix;

    // Java interop (Java.type) is OFF by default for mocks: a mock can assign attacker-controlled
    // request data (request/requestHeaders/requestParams), which is then processed for embedded
    // expressions — leaving the Java bridge on would make that a remote code execution vector.
    // A trusted mock can opt back in via the builder flag or `configure javaBridgeEnabled = true`.
    private final boolean javaBridgeEnabled;

    // Whether request-derived data (request/requestHeaders/requestParams) has its embedded
    // `#(...)` expressions evaluated. OFF by default so attacker-controlled request data stays
    // inert; opt back in via the builder flag or `configure requestExpressionsEnabled = true`.
    private final boolean requestExpressionsEnabled;

    // Runtime per feature (like V1's scenarioRuntimes map)
    private final Map<Feature, ScenarioRuntime> runtimes = new LinkedHashMap<>();

    // Constructed from feature file path
    public MockHandler(String featurePath) {
        this(Feature.read(featurePath), null);
    }

    public MockHandler(Feature feature) {
        this(feature, null);
    }

    public MockHandler(Feature feature, Map<String, Object> args) {
        this(List.of(feature), args, null);
    }

    public MockHandler(List<Feature> features, Map<String, Object> args, String pathPrefix) {
        this(features, args, pathPrefix, false, false);
    }

    public MockHandler(List<Feature> features, Map<String, Object> args, String pathPrefix,
                       boolean javaBridgeEnabled, boolean requestExpressionsEnabled) {
        this.pathPrefix = pathPrefix;
        this.javaBridgeEnabled = javaBridgeEnabled;
        this.requestExpressionsEnabled = requestExpressionsEnabled;

        // Initialize each feature with its own runtime
        for (Feature feature : features) {
            this.features.add(feature);
            ScenarioRuntime runtime = initRuntime(feature, args);
            runtimes.put(feature, runtime);
        }

        logger.info("mock handler initialized with {} feature(s), cors: {}", features.size(), config.isCorsEnabled());
    }

    /**
     * Release the HTTP clients belonging to this handler's cached per-feature runtimes.
     *
     * <p>Each mock feature gets a {@code ScenarioRuntime} that owns a client, and those runtimes
     * live as long as the handler rather than as long as a scenario — so the ordinary
     * scenario-teardown release never reaches them. The client only ever builds a real transport
     * if the mock makes outbound requests ({@code karate.proceed}, proxy mocks), which bounds the
     * cost, but it is one abandoned client per feature per handler and a dev-mode reload mints a
     * whole new handler on every file change.
     *
     * <p>Idempotent, because {@code releaseHttpClient} is.
     */
    void releaseHttpClients() {
        for (ScenarioRuntime runtime : runtimes.values()) {
            runtime.releaseHttpClient();
        }
    }

    /**
     * Initialize a runtime for a mock feature.
     * Creates a proper FeatureRuntime and ScenarioRuntime like V1 does.
     */
    @SuppressWarnings("unchecked")
    private ScenarioRuntime initRuntime(Feature feature, Map<String, Object> args) {
        // Create FeatureRuntime (without Suite to skip karate-config.js loading)
        FeatureRuntime featureRuntime = new FeatureRuntime(null, feature);

        // Get first scenario for the runtime, or create a dummy one
        Scenario scenario = getFirstScenario(feature);

        // Create ScenarioRuntime with the FeatureRuntime (enables proper resource resolution)
        ScenarioRuntime runtime = new ScenarioRuntime(featureRuntime, scenario);

        // Wire up MockHandler reference for karate.proceed()
        runtime.getKarate().setMockHandler(this);

        // Disable Java interop by default for the mock engine (see javaBridgeEnabled). Done
        // before Background runs so that a `configure javaBridgeEnabled = true` step there can
        // opt back in. When the builder flag is set, leave the default (enabled) bridge in place.
        runtime.getKarate().setJavaBridgeEnabled(javaBridgeEnabled);
        // Likewise treat request-derived data as inert by default; Background may opt back in.
        runtime.setRequestExpressionsEnabled(requestExpressionsEnabled);

        // Register matcher functions (lambdas read from currentRequest field)
        Engine engine = runtime.getEngine();
        engine.put("pathMatches", (JavaInvokable) a -> {
            if (currentRequest == null) return false;
            boolean matched = currentRequest.pathMatches(a[0] + "");
            if (matched) {
                engine.put("pathParams", currentRequest.getPathParams());
            }
            return matched;
        });
        engine.put("methodIs", (JavaInvokable) a ->
            currentRequest != null && (a[0] + "").equalsIgnoreCase(currentRequest.getMethod()));
        engine.put("typeContains", (JavaInvokable) a -> {
            if (currentRequest == null) return false;
            String contentType = currentRequest.getContentType();
            return contentType != null && contentType.contains(a[0] + "");
        });
        engine.put("acceptContains", (JavaInvokable) a -> {
            if (currentRequest == null) return false;
            String accept = currentRequest.getHeader("Accept");
            return accept != null && accept.contains(a[0] + "");
        });
        engine.put("headerContains", (JavaInvokable) a -> {
            if (currentRequest == null) return false;
            List<String> values = currentRequest.getHeaderValues(a[0] + "");
            if (values != null) {
                String search = a[1] + "";
                for (String v : values) {
                    if (v.contains(search)) return true;
                }
            }
            return false;
        });
        engine.put("headerValue", (JavaInvokable) a ->
            currentRequest != null && a.length > 0
                ? markRequestDerived(runtime, currentRequest.getHeader(a[0] + "")) : null);
        engine.put("paramValue", (JavaInvokable) a ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getParam(a[0] + "")) : null);
        engine.put("paramExists", (JavaInvokable) a -> {
            if (currentRequest == null) return false;
            List<String> values = currentRequest.getParamValues(a[0] + "");
            return values != null && !values.isEmpty();
        });
        engine.put("bodyPath", (JavaInvokable) a -> {
            if (currentRequest == null) return null;
            Object body = currentRequest.getBodyConverted();
            if (body == null) return null;
            String path = a[0] + "";
            if (path.startsWith("/")) {
                // XPath for XML
                if (body instanceof Node) {
                    return markRequestDerived(runtime, Xml.getTextValueByPath((Node) body, path));
                }
                return null;
            } else {
                // JsonPath for JSON
                try {
                    return markRequestDerived(runtime, JsonPath.read(body, path));
                } catch (Exception e) {
                    logger.debug("bodyPath evaluation failed: {}", e.getMessage());
                    return null;
                }
            }
        });

        // Register lazy request variables (resolved via JsLazy when accessed)
        // These read from currentRequest field which is set per-request
        engine.put("request", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getBodyConverted()) : null);
        engine.put("requestBytes", (JsLazy) () ->
            currentRequest != null ? currentRequest.getBody() : null);
        engine.put("requestPath", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getPath()) : null);
        engine.put("requestUri", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getPathRaw()) : null);
        engine.put("requestUrlBase", (JsLazy) () ->
            currentRequest != null ? currentRequest.jsGet("urlBase") : null);
        engine.put("requestMethod", (JsLazy) () ->
            currentRequest != null ? currentRequest.getMethod() : null);
        engine.put("requestHeaders", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getHeaders()) : null);
        engine.put("requestParams", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getParams()) : null);
        engine.put("requestParts", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getMultiParts()) : null);
        engine.put("requestCookies", (JsLazy) () ->
            currentRequest != null ? markRequestDerived(runtime, currentRequest.getCookies()) : null);

        // Put args into globals if provided
        if (args != null) {
            globals.putAll(args);
            for (var entry : args.entrySet()) {
                engine.put(entry.getKey(), entry.getValue());
            }
        }

        // Execute background once on initialization using StepExecutor
        StepExecutor executor = new StepExecutor(runtime);
        if (feature.isBackgroundPresent()) {
            for (Step step : feature.getBackground().getSteps()) {
                StepResult result = executor.execute(step);
                if (result.isFailed()) {
                    throw new RuntimeException("mock background failed at line " + step.getLine() + ": " +
                        result.getError().getMessage(), result.getError());
                }
            }
            // Save background variables to globals
            saveGlobals(engine);

            // Transfer configure settings to MockConfig
            KarateConfig karateConfig = runtime.getConfig();
            if (karateConfig.isCorsEnabled()) {
                config.setCorsEnabled(true);
            }
            Object responseHeaders = karateConfig.getResponseHeaders();
            if (responseHeaders instanceof Map) {
                config.setResponseHeaders((Map<String, Object>) responseHeaders);
            }
            Object beforeScenario = karateConfig.getBeforeScenario();
            if (beforeScenario instanceof JavaCallable callable) {
                config.setBeforeScenario(callable);
            }
            Object afterScenario = karateConfig.getAfterScenario();
            if (afterScenario instanceof JavaCallable callable) {
                config.setAfterScenario(callable);
            }
        }

        logger.debug("initialized feature: {}", feature);
        return runtime;
    }

    private Scenario getFirstScenario(Feature feature) {
        for (FeatureSection section : feature.getSections()) {
            if (section.getScenario() != null) {
                return section.getScenario();
            }
        }
        // Fallback - create minimal feature with scenario
        Feature minimalFeature = Feature.read(Resource.text("Feature: Mock\nScenario: dummy\n* def x = 1"));
        return minimalFeature.getSections().getFirst().getScenario();
    }

    /**
     * Save current engine variables to globals for persistence across requests.
     */
    private void saveGlobals(Engine engine) {
        Map<String, Object> bindings = engine.getBindings();
        for (var entry : bindings.entrySet()) {
            String key = entry.getKey();
            // Skip built-in variables and request-specific variables
            if (!isBuiltInVariable(key)) {
                globals.put(key, entry.getValue());
            }
        }
    }

    private boolean isBuiltInVariable(String name) {
        return name.equals("karate") || name.equals("read") || name.equals("match") ||
               name.startsWith("request") || name.startsWith("response") ||
               name.equals("pathParams") || name.equals("pathMatches") ||
               name.equals("methodIs") || name.equals("typeContains") ||
               name.equals("acceptContains") || name.equals("headerContains") ||
               name.equals("headerValue") || name.equals("paramValue") ||
               name.equals("paramExists") || name.equals("bodyPath");
    }

    @Override
    public HttpResponse apply(HttpRequest request) {
        requestLock.lock();
        try {
            proceeded = false;
            HttpResponse response = handleRequest(request);
            // Disclose that this response was FABRICATED by a karate mock. Downstream — above all the
            // coverage graph — cannot otherwise tell a stand-in from the real system: an address is no
            // signal (a real service is routinely on localhost), so the mock has to say so itself.
            // Presence is certainty; absence proves nothing (any other stub answers no differently).
            // A karate.proceed() response came from the real upstream, so it is deliberately NOT stamped.
            if (config.isMockHeaderEnabled() && !proceeded) {
                response.setHeader(KARATE_MOCK_HEADER, "true");
            }
            return response;
        } finally {
            requestLock.unlock();
        }
    }

    private HttpResponse handleRequest(HttpRequest request) {
        // Handle CORS preflight
        if (config.isCorsEnabled() && "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return handleCorsPreFlight(request);
        }

        // Strip path prefix if configured
        if (pathPrefix != null && request.getPath().startsWith(pathPrefix)) {
            request.setPath(request.getPath().substring(pathPrefix.length()));
        }

        // Process body for form-urlencoded and multipart
        request.processBody();

        // Find matching scenario and execute
        for (Feature feature : features) {
            ScenarioRuntime runtime = runtimes.get(feature);
            Engine engine = runtime.getEngine();

            // Drop any values marked request-derived by a previous request before this one's
            // bindings are read again, so the identity set never accumulates stale entries.
            runtime.clearRequestDerived();

            // Set up request variables (includes storing HttpRequest for matcher functions)
            setupRequestVariables(engine, request);

            for (FeatureSection section : feature.getSections()) {
                if (section.isOutline()) {
                    logger.warn("skipping scenario outline in mock - {}:{}", feature, section.getScenarioOutline().getLine());
                    continue;
                }

                Scenario scenario = section.getScenario();
                if (isMatchingScenario(scenario, engine)) {
                    return executeScenario(runtime, scenario, request);
                }
            }
        }

        // No match found - return 404
        logger.warn("no scenarios matched, returning 404: {} {}", request.getMethod(), request.getPath());
        return createNotFoundResponse();
    }

    private HttpResponse handleCorsPreFlight(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setStatus(200);
        response.setHeader("Allow", ALLOWED_METHODS);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
        List<String> requestHeaders = request.getHeaderValues("Access-Control-Request-Headers");
        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            response.setHeader("Access-Control-Allow-Headers", requestHeaders.toArray(new String[0]));
        }
        return response;
    }

    // Tag request-derived data so its embedded #(...) expressions stay inert (unless the mock opted in
    // via requestExpressionsEnabled — the OPT-IN is applied where the expression would be evaluated, not
    // here, so this stays a pure PROVENANCE record). Two consumers now depend on that separation: the
    // inertness short-circuit, and the stranded-placeholder guard in buildResponse, which must never
    // mistake attacker-supplied text echoed back for a mock author's own broken expression.
    // Returns the value for inline use in the bindings.
    private static Object markRequestDerived(ScenarioRuntime runtime, Object value) {
        runtime.markRequestDerived(value);
        return value;
    }

    private void setupRequestVariables(Engine engine, HttpRequest request) {
        // Set current request - JsLazy bindings and matcher functions read from this field
        this.currentRequest = request;

        // Parse multipart/form-urlencoded body so fields are available in requestParams
        request.processBody();

        // Set all globals
        for (Map.Entry<String, Object> entry : globals.entrySet()) {
            engine.put(entry.getKey(), entry.getValue());
        }

        // Initialize response variables with defaults (must be reset per request)
        engine.put("response", null);
        engine.put("responseStatus", 200);
        engine.put("responseStatusText", null);
        engine.put("responseHeaders", new HashMap<>());
        engine.put("responseDelay", 0);
        engine.put("pathParams", new HashMap<>());
    }

    private boolean isMatchingScenario(Scenario scenario, Engine engine) {
        String expression = StringUtils.trimToNull(scenario.getName());

        // Empty/null expression means catch-all (always matches)
        if (expression == null) {
            logger.debug("catch-all scenario matched at line: {}", scenario.getLine());
            return true;
        }

        try {
            Object result = engine.eval(expression);
            if (Boolean.TRUE.equals(result)) {
                logger.debug("scenario matched at line {}: {}", scenario.getLine(), expression);
                return true;
            } else {
                logger.trace("scenario skipped at line {}: {}", scenario.getLine(), expression);
                return false;
            }
        } catch (Exception e) {
            logger.warn("scenario match evaluation failed at line {}: {} - {}", scenario.getLine(), expression, e.getMessage());
            return false;
        }
    }

    private HttpResponse executeScenario(ScenarioRuntime runtime, Scenario scenario, HttpRequest request) {
        Engine engine = runtime.getEngine();
        StepExecutor executor = new StepExecutor(runtime);

        // Execute beforeScenario hook before step execution so request-scoped setup can run per-request.
        // A hook exception surfaces as HTTP 500 (same as a step failure) - wrap the hook body in
        // try/catch if you want to suppress errors.
        Exception beforeError = invokeMockHook(config.getBeforeScenario(), "beforeScenario");
        if (beforeError != null) {
            return hookErrorResponse("beforeScenario", beforeError);
        }

        // Execute all steps in the scenario using StepExecutor
        for (Step step : scenario.getSteps()) {
            StepResult result = executor.execute(step);
            if (result.isFailed()) {
                logger.error("step execution failed at line {}: {}", step.getLine(), result.getError().getMessage());
                return HttpResponse.error(500, result.getError().getMessage());
            }
        }

        // Save any new variables to globals
        saveGlobals(engine);

        // Execute afterScenario hook if configured.
        // Pass null context - the JS function uses its declaredContext which has access to karate object.
        // A hook exception surfaces as HTTP 500 (same convention as beforeScenario / step failures).
        Exception afterError = invokeMockHook(config.getAfterScenario(), "afterScenario");
        if (afterError != null) {
            return hookErrorResponse("afterScenario", afterError);
        }

        // Build response from variables
        return buildResponse(runtime, engine, request);
    }

    private Exception invokeMockHook(JavaCallable hook, String hookName) {
        if (hook == null) {
            return null;
        }
        try {
            hook.call(null);
            return null;
        } catch (Exception e) {
            logger.warn("{} hook failed: {}", hookName, e.getMessage());
            return e;
        }
    }

    private HttpResponse hookErrorResponse(String hookName, Exception error) {
        return HttpResponse.error(500, hookName + " hook failed: " + error.getMessage());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse buildResponse(ScenarioRuntime runtime, Engine engine, HttpRequest request) {
        HttpResponse response = new HttpResponse();

        // Get response variables from engine
        Object responseBody = engine.get("response");
        Object responseStatus = engine.get("responseStatus");
        Object responseStatusText = engine.get("responseStatusText");
        Object responseHeaders = engine.get("responseHeaders");
        Object responseDelay = engine.get("responseDelay");

        // Handle karate.proceed() result - if response is an HttpResponse, pass it through
        if (responseBody instanceof HttpResponse proceedResponse) {
            // the real upstream answered this one — apply() must not stamp it as mock-fabricated
            proceeded = true;
            // Pass through the proceed response directly
            response.setStatus(proceedResponse.getStatus());
            response.setStatusText(proceedResponse.getStatusText());
            response.setBody(proceedResponse.getBodyBytes(), proceedResponse.getResourceType());
            if (proceedResponse.getHeaders() != null) {
                response.setHeaders(proceedResponse.getHeaders());
            }
            // Apply CORS if enabled (still need to add this after)
            if (config.isCorsEnabled()) {
                response.setHeader("Access-Control-Allow-Origin", "*");
            }
            return response;
        }

        // Set status
        if (responseStatus instanceof Number) {
            response.setStatus(((Number) responseStatus).intValue());
        }
        if (responseStatusText instanceof String s) {
            response.setStatusText(s);
        }

        // Apply configured response headers first
        Map<String, Object> configuredHeaders = config.getResponseHeaders();
        if (configuredHeaders != null) {
            response.setHeaders(configuredHeaders);
        }

        // Apply scenario-level response headers (override configured)
        if (responseHeaders instanceof Map) {
            response.setHeaders((Map<String, Object>) responseHeaders);
        }

        // Add CORS header if enabled
        if (config.isCorsEnabled()) {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }

        if (responseBody != null) {
            // A `#(expr)` that throws is deliberately left as its own SOURCE TEXT (StepExecutor) — the
            // schema-as-template pattern, where the match engine re-resolves it later with the right
            // context. A mock response is the one place that recovery can never happen: nothing matches a
            // mock body, so the placeholder is written to the wire AS DATA. That is silent corruption of
            // the exact kind a mock exists to avoid — a mock whose id helper threw served every consumer
            // the literal string "#(uuid())" and no test could see it.
            //
            // The check is over the failures the RUNTIME RECORDED, never over "does this look like a
            // placeholder". The difference is the whole safety of it: a `#(...)` in a finished body may
            // be echoed request data (`#(pathParams.id)` over a path a client chose), or a value some
            // other expression computed, and neither was ever an expression of this mock's. Judging by
            // appearance turns text a caller controls into a 500 they can trigger at will.
            if (runtime.hasFailedEmbedded()) {
                String stranded = strandedEmbedded(runtime, responseBody, "");
                if (stranded != null) {
                    return HttpResponse.error(500, strandedMessage(runtime, stranded));
                }
            }
            response.setBodyDynamic(responseBody);
        }

        // Set response delay (handled by HttpServerHandler using Netty scheduler)
        if (responseDelay instanceof Number) {
            int delay = ((Number) responseDelay).intValue();
            if (delay > 0) {
                response.setDelay(delay);
            }
        }

        return response;
    }

    /**
     * The first value in a mock response body that is a placeholder <b>this run recorded a failure for</b>
     * — as {@code <json-pointer> <placeholder>} — or null when the body is clean.
     *
     * <p>Membership in the recorded set is the whole test. Looking for something that merely <i>reads</i>
     * like a placeholder would fire on echoed request data and on values other expressions produced, which
     * is a 500 a caller can trigger; and it would still be wrong in the other direction, since an
     * {@code ##(...)} that failed leniently means <i>may be absent</i> and is not an error at all.</p>
     */
    @SuppressWarnings("unchecked")
    private static String strandedEmbedded(ScenarioRuntime runtime, Object value, String path) {
        if (value instanceof String s && runtime.failedEmbedded(s) != null) {
            return path + ' ' + s;
        }
        if (value instanceof Node node) {
            // an XML body is text under the hood — the placeholder survives in a node value, and the same
            // recorded-failure test applies
            String xml = Xml.toString(node, false);
            for (String placeholder : runtime.failedEmbeddedKeys()) {
                if (xml.contains(placeholder)) {
                    return path + ' ' + placeholder;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
                String found = strandedEmbedded(runtime, e.getValue(), path + "/" + e.getKey());
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String found = strandedEmbedded(runtime, list.get(i), path + "/" + i);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Name the cause, not just the symptom — reporting the ORIGINAL throwable. It is not re-evaluated:
     * re-running a user expression on an error path would repeat any side effect outside the engine, and
     * an expression that blocks would stall every other request, since this runs under the request lock.
     */
    private static String strandedMessage(ScenarioRuntime runtime, String stranded) {
        int sep = stranded.indexOf(' ');
        String at = stranded.substring(0, sep);
        String placeholder = stranded.substring(sep + 1);
        Exception error = runtime.failedEmbedded(placeholder);
        return "mock response would have served an UNEVALUATED embedded expression as data"
                + (at.isEmpty() ? "" : " at " + at) + ": " + placeholder
                + (error == null || error.getMessage() == null ? "" : " — " + error.getMessage())
                + ". A mock body is never re-resolved by the match engine, so this would have gone out on "
                + "the wire as the literal string. Fix the expression, or quote it differently if the "
                + "literal text really is what you meant to return.";
    }

    private HttpResponse createNotFoundResponse() {
        return HttpResponse.notFound("no matching scenario");
    }

    // ===== Accessors =====

    public MockConfig getConfig() {
        return config;
    }

    public Map<String, Object> getGlobals() {
        return globals;
    }

    public Object getVariable(String name) {
        return globals.get(name);
    }

}
