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

import io.karatelabs.common.DataUtils;
import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.common.StringUtils;
import io.karatelabs.common.Xml;
import org.w3c.dom.Node;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.MatchExpression;
import io.karatelabs.gherkin.Step;
import io.karatelabs.gherkin.Table;
import io.karatelabs.http.AuthHandler;
import io.karatelabs.http.BasicAuthHandler;
import io.karatelabs.http.BearerAuthHandler;
import io.karatelabs.http.ClientCredentialsAuthHandler;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpRequestBuilder;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.gherkin.GherkinParser;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import io.karatelabs.match.Match;
import io.karatelabs.match.Result;

import com.jayway.jsonpath.JsonPath;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StepExecutor {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    private final ScenarioRuntime runtime;

    // Call results accumulated during current step execution (for call/callonce steps)
    private List<FeatureResult> stepCallResults;

    public StepExecutor(ScenarioRuntime runtime) {
        this.runtime = runtime;
    }

    private void addCallResult(FeatureResult result) {
        if (stepCallResults == null) {
            stepCallResults = new ArrayList<>();
        }
        stepCallResults.add(result);
    }

    private Suite getSuite() {
        FeatureRuntime fr = runtime.getFeatureRuntime();
        return fr != null ? fr.getSuite() : null;
    }

    public StepResult execute(Step step) {
        long startTime = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        stepCallResults = null;  // Clear for this step

        // Fire STEP_ENTER event
        Suite suite = getSuite();
        if (suite != null) {
            boolean proceed = suite.fireEvent(StepRunEvent.enter(step, runtime));
            if (!proceed) {
                // Listener returned false - skip this step
                StepResult skipped = StepResult.skipped(step, startTime);
                collectLogsAndEmbeds(skipped);
                return skipped;
            }
        }

        // Check interceptor for debug support
        @SuppressWarnings("unchecked")
        io.karatelabs.js.RunInterceptor<Object> interceptor = (io.karatelabs.js.RunInterceptor<Object>) runtime.getInterceptor();
        io.karatelabs.js.DebugPointFactory<Object> pointFactory = null;
        Object point = null;
        if (interceptor != null && runtime.getPointFactory() != null) {
            @SuppressWarnings("unchecked")
            io.karatelabs.js.DebugPointFactory<Object> factory = (io.karatelabs.js.DebugPointFactory<Object>) runtime.getPointFactory();
            pointFactory = factory;
            String sourcePath = step.getFeature().getResource().getRelativePath();
            point = factory.create(io.karatelabs.js.DebugPointFactory.GHERKIN_STEP, step.getLine(), sourcePath, step, null);
            io.karatelabs.js.RunInterceptor.Action action = interceptor.beforeExecute(point);
            if (action == io.karatelabs.js.RunInterceptor.Action.SKIP) {
                StepResult skipped = StepResult.skipped(step, startTime);
                collectLogsAndEmbeds(skipped);
                if (suite != null) {
                    suite.fireEvent(StepRunEvent.exit(skipped, runtime));
                }
                return skipped;
            } else if (action == io.karatelabs.js.RunInterceptor.Action.WAIT) {
                action = interceptor.waitForResume();
                if (action == io.karatelabs.js.RunInterceptor.Action.SKIP) {
                    StepResult skipped = StepResult.skipped(step, startTime);
                    collectLogsAndEmbeds(skipped);
                    if (suite != null) {
                        suite.fireEvent(StepRunEvent.exit(skipped, runtime));
                    }
                    return skipped;
                }
            }
        }
        // Keep these references for use in try/catch blocks
        final io.karatelabs.js.RunInterceptor<Object> finalInterceptor = interceptor;
        final io.karatelabs.js.DebugPointFactory<Object> finalPointFactory = pointFactory;
        final Object finalPoint = point;

        try {
            String keyword = step.getKeyword();

            String text = step.getText();
            // Check if keyword contains punctuation - means it's a JS expression
            // Examples: foo.bar, foo(), foo['bar'].baz('blah')
            // Note: cases like foo[x] are handled by the lexer (rewinds to GS_RHS mode)
            if (keyword != null && StepUtils.hasPunctuation(keyword)) {
                String fullExpr = keyword + text;
                if (step.getDocString() != null) {
                    fullExpr = fullExpr + step.getDocString();
                }
                runtime.eval(fullExpr, step);
            } else if (keyword == null) {
                // Plain expression (e.g., "* print foo" or "* foo.bar()")
                executeExpression(step);
            } else {
                switch (keyword) {
                    // Variable assignment
                    case "def" -> executeDef(step);
                    case "set" -> executeSet(step);
                    case "remove" -> executeRemove(step);
                    case "text" -> executeText(step);
                    case "json" -> executeJson(step);
                    case "xml" -> executeXml(step);
                    case "xmlstring" -> executeXmlString(step);
                    case "string" -> executeString(step);
                    case "csv" -> executeCsv(step);
                    case "yaml" -> executeYaml(step);
                    case "copy" -> executeCopy(step);
                    case "table" -> executeTable(step);
                    case "replace" -> executeReplace(step);

                    // Assertions
                    case "match" -> executeMatch(step);
                    case "assert" -> executeAssert(step);
                    case "print" -> executePrint(step);

                    // HTTP
                    case "url" -> executeUrl(step);
                    case "path" -> executePath(step);
                    case "param" -> executeParam(step);
                    case "params" -> executeParams(step);
                    case "header" -> executeHeader(step);
                    case "headers" -> executeHeaders(step);
                    case "cookie" -> executeCookie(step);
                    case "cookies" -> executeCookies(step);
                    case "form field" -> executeFormField(step);
                    case "form fields" -> executeFormFields(step);
                    case "request" -> executeRequest(step);
                    case "retry until" -> executeRetryUntil(step);
                    case "method" -> executeMethod(step);
                    case "soap action" -> executeSoapAction(step);
                    case "status" -> executeStatus(step);
                    case "multipart file" -> executeMultipartFile(step);
                    case "multipart field" -> executeMultipartField(step);
                    case "multipart fields" -> executeMultipartFields(step);
                    case "multipart files" -> executeMultipartFiles(step);
                    case "multipart entity" -> executeMultipartEntity(step);

                    // Control flow
                    case "call" -> executeCall(step);
                    case "callonce" -> executeCallOnce(step);
                    case "eval" -> executeEval(step);
                    case "doc" -> executeDoc(step);

                    // Config
                    case "configure" -> executeConfigure(step);

                    // Browser driver
                    case "driver" -> executeDriver(step);

                    default -> {
                        // Check if text starts with ( - means keyword is a function name like myFunc('a')
                        // We only do this check here (not earlier) because valid keywords like "eval"
                        // could have text starting with ( like "eval (1 + 2)"
                        if (text != null && text.trim().startsWith("(")) {
                            runtime.eval(keyword + text, step);
                        } else {
                            throw new RuntimeException("unknown keyword: " + keyword);
                        }
                    }
                }
            }

            long elapsedNanos = System.nanoTime() - startNanos;
            StepResult result = StepResult.passed(step, startTime, elapsedNanos);
            collectLogsAndEmbeds(result);

            // Notify interceptor of successful step execution
            if (finalInterceptor != null && finalPointFactory != null) {
                Object afterPoint = finalPoint;
                if (afterPoint == null) {
                    String sourcePath = step.getFeature().getResource().getRelativePath();
                    afterPoint = finalPointFactory.create(io.karatelabs.js.DebugPointFactory.GHERKIN_STEP, step.getLine(), sourcePath, step, null);
                }
                finalInterceptor.afterExecute(afterPoint, result, null);
            }

            // Fire STEP_EXIT event
            if (suite != null) {
                suite.fireEvent(StepRunEvent.exit(result, runtime));
            }

            return result;

        } catch (AssertionError | Exception e) {
            long elapsedNanos = System.nanoTime() - startNanos;
            StepResult result = StepResult.failed(step, startTime, elapsedNanos, e);
            collectLogsAndEmbeds(result);

            // Notify interceptor of failed step execution
            if (finalInterceptor != null && finalPointFactory != null) {
                Object afterPoint = finalPoint;
                if (afterPoint == null) {
                    String sourcePath = step.getFeature().getResource().getRelativePath();
                    afterPoint = finalPointFactory.create(io.karatelabs.js.DebugPointFactory.GHERKIN_STEP, step.getLine(), sourcePath, step, null);
                }
                finalInterceptor.afterExecute(afterPoint, result, e);
            }

            // Fire STEP_EXIT event
            if (suite != null) {
                suite.fireEvent(StepRunEvent.exit(result, runtime));
            }

            return result;
        }
    }

    private void collectLogsAndEmbeds(StepResult result) {
        LogContext ctx = LogContext.get();
        result.setLog(ctx.collect());
        java.util.List<StepResult.Embed> embeds = ctx.collectEmbeds();
        if (embeds != null) {
            for (StepResult.Embed embed : embeds) {
                result.addEmbed(embed);
            }
        }
        // Set call results accumulated during this step's execution
        if (stepCallResults != null && !stepCallResults.isEmpty()) {
            result.setCallResults(stepCallResults);
        }
    }

    // ========== Expression Execution ==========

    private void executeExpression(Step step) {
        runtime.eval(step.getText(), step);
    }

    // ========== Variable Assignment ==========

    private void executeDef(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        if (eqIndex < 0) {
            throw new RuntimeException("def requires '=' assignment: " + text);
        }
        String name = text.substring(0, eqIndex).trim();
        validateVariableName(name);
        String expr = text.substring(eqIndex + 1).trim();

        // Handle docstring if expression is empty
        if (expr.isEmpty() && step.getDocString() != null) {
            expr = step.getDocString();
        }

        // Check if RHS is a special karate expression (not standard JS)
        if (expr.startsWith("call ")) {
            String callExpr = expr.substring(5).trim();
            executeCallWithResult(callExpr, name);
        } else if (expr.startsWith("callonce ")) {
            String callExpr = expr.substring(9).trim();
            executeCallOnceWithResult(callExpr, name);
        } else {
            // Use common Karate expression evaluation which handles:
            // - $varname/xpath, $varname[*].path (jsonpath)
            // - get[N] varname path
            // - <xml> literals
            // - varname/xpath (XML XPath shorthand)
            // - Regular JS expressions
            Object value = evalKarateExpression(expr);
            runtime.setVariable(name, value);
        }
    }

    private void executeCallWithResult(String callExpr, String resultVar) {
        // Try to evaluate the first token to see if it's a JS function or Feature
        // Syntax: "fun" or "fun arg" where fun is a JS function variable or Feature
        // Note: read('file.js') returns a JS function, read('file.feature') returns a Feature
        int spaceIdx = StepUtils.findCallArgSeparator(callExpr);
        String firstToken = spaceIdx > 0 ? callExpr.substring(0, spaceIdx) : callExpr;

        // Check if it's a read() call for a literal feature file path - use parseCallExpression
        // which properly handles embedded expressions like #(nodes)
        // For read(variable) or read('file.js'), we evaluate to determine the type
        if (StepUtils.isLiteralFeatureRead(callExpr)) {
            // Fall through to standard feature call handling
        } else {
            // Try to evaluate as a JS expression (handles variables, read('file.js'), etc.)
            try {
                Object evaluated = runtime.eval(firstToken);
                if (evaluated instanceof JavaCallable fn) {
                    // It's a JS function - invoke it and store result
                    Object arg = null;
                    if (spaceIdx > 0) {
                        String argExpr = callExpr.substring(spaceIdx + 1).trim();
                        if (!argExpr.isEmpty()) {
                            arg = runtime.eval(argExpr);
                            // V1 compatibility: process embedded expressions like #(var) in call arguments
                            if (arg instanceof Map) {
                                arg = processEmbeddedExpressions((Map<?, ?>) arg);
                            }
                        }
                    }
                    Object result = arg != null
                            ? fn.call(null, new Object[]{arg})
                            : fn.call(null, new Object[0]);
                    runtime.setVariable(resultVar, result);
                    return;
                } else if (evaluated instanceof FeatureCall || evaluated instanceof Feature) {
                    // It's a Feature or FeatureCall - call it with arguments
                    String argExpr = spaceIdx > 0 ? callExpr.substring(spaceIdx + 1).trim() : null;
                    executeFeatureCall(evaluated, argExpr, resultVar);
                    return;
                }
            } catch (Exception e) {
                // Not a valid JS expression, fall through to feature call
            }
        }

        // Standard feature call
        CallExpression call = parseCallExpression(callExpr);
        call.resultVar = resultVar;

        // Resolve the feature file relative to current feature
        FeatureRuntime fr = runtime.getFeatureRuntime();
        Feature calledFeature;

        if (call.sameFile) {
            // Same-file tag call - use current feature
            calledFeature = fr != null ? fr.getFeature() : null;
            if (calledFeature == null) {
                throw new RuntimeException("call with tag selector requires a feature context");
            }
        } else {
            Resource calledResource = fr != null
                    ? fr.resolve(call.path)
                    : Resource.path(call.path);
            calledFeature = Feature.read(calledResource);
        }

        // Check if it's an array loop call
        if (call.argList != null) {
            List<Map<String, Object>> results = new ArrayList<>();
            int loopIndex = 0;
            for (Object item : call.argList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> callArg = item instanceof Map ? (Map<String, Object>) item : null;

                FeatureRuntime nestedFr = new FeatureRuntime(
                        fr != null ? fr.getSuite() : null,
                        calledFeature,
                        fr,
                        runtime,
                        false,  // Isolated scope
                        callArg,
                        call.tagSelector
                );
                nestedFr.setLoopIndex(loopIndex);

                FeatureResult featureResult = nestedFr.call();
                addCallResult(featureResult);

                if (nestedFr.getLastExecuted() != null) {
                    results.add(nestedFr.getLastExecuted().getAllVariables());
                }
                loopIndex++;
            }
            runtime.setVariable(resultVar, results);
            return;
        }

        // Single call
        FeatureRuntime nestedFr = new FeatureRuntime(
                fr != null ? fr.getSuite() : null,
                calledFeature,
                fr,
                runtime,
                false,  // Isolated scope - copy variables, don't share
                call.arg,
                call.tagSelector
        );

        // Execute the called feature
        FeatureResult featureResult = nestedFr.call();

        // Capture feature result for HTML report display (V1 style)
        addCallResult(featureResult);

        // Capture result variables from the last executed scenario (isolated scope)
        if (nestedFr.getLastExecuted() != null) {
            Map<String, Object> resultVars = nestedFr.getLastExecuted().getAllVariables();
            runtime.setVariable(resultVar, resultVars);
        }
    }

    private void executeCallOnceWithResult(String callExpr, String resultVar) {
        String cacheKey = "callonce:" + callExpr;

        // Use feature-level cache (not suite-level) - callOnce is scoped per feature
        FeatureRuntime fr = runtime.getFeatureRuntime();
        if (fr == null) {
            // No feature context - just execute normally
            executeCallWithResult(callExpr, resultVar);
            return;
        }

        Map<String, Object> cache = fr.CALLONCE_CACHE;
        java.util.concurrent.locks.ReentrantLock lock = fr.getCallOnceLock();

        // Fast path - check cache without lock
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            // Return deep copy to prevent cross-scenario mutation
            runtime.setVariable(resultVar, StepUtils.deepCopy(cached));
            return;
        }

        // Slow path - acquire lock for execution
        lock.lock();
        try {
            // Double-check after acquiring lock
            Object rechecked = cache.get(cacheKey);
            if (rechecked != null) {
                runtime.setVariable(resultVar, StepUtils.deepCopy(rechecked));
                return;
            }

            // Not cached - execute the call
            executeCallWithResult(callExpr, resultVar);

            // Cache a deep copy of the result (may be Map for single call or List for loop call)
            Object resultValue = runtime.getVariable(resultVar);
            if (resultValue != null) {
                cache.put(cacheKey, StepUtils.deepCopy(resultValue));
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private void executeFeatureCall(Feature calledFeature, Object arg, String resultVar) {
        FeatureRuntime fr = runtime.getFeatureRuntime();

        if (arg instanceof List) {
            // Loop call - iterate over list and collect results
            List<Object> argList = (List<Object>) arg;
            List<Map<String, Object>> results = new ArrayList<>();
            for (Object item : argList) {
                Map<String, Object> argMap = item instanceof Map
                        ? (Map<String, Object>) item : null;
                Map<String, Object> result = callFeatureOnce(calledFeature, fr, argMap);
                results.add(result);
            }
            runtime.setVariable(resultVar, results);
        } else {
            // Single call
            Map<String, Object> argMap = arg instanceof Map
                    ? (Map<String, Object>) arg : null;
            Map<String, Object> result = callFeatureOnce(calledFeature, fr, argMap);
            runtime.setVariable(resultVar, result);
        }
    }

    private Map<String, Object> callFeatureOnce(Feature calledFeature, FeatureRuntime parentFr, Map<String, Object> arg) {
        // Create nested FeatureRuntime with isolated scope (sharedScope=false)
        FeatureRuntime nestedFr = new FeatureRuntime(
                parentFr != null ? parentFr.getSuite() : null,
                calledFeature,
                parentFr,
                runtime,
                false,  // Isolated scope - copy variables, don't share
                arg
        );

        // Execute the called feature
        FeatureResult featureResult = nestedFr.call();

        // Capture feature result for HTML report display (V1 style)
        addCallResult(featureResult);

        // Capture result variables from the last executed scenario (isolated scope)
        if (nestedFr.getLastExecuted() != null) {
            return nestedFr.getLastExecuted().getAllVariables();
        }
        return new HashMap<>();
    }

    /**
     * Propagate results from a called feature back to the caller.
     * Handles both isolated scope (resultVar set) and shared scope (resultVar null).
     * For shared scope, also propagates config, cookies, and driver (if scope: 'caller').
     */
    private void propagateFromCallee(ScenarioRuntime calleeRuntime, String resultVar) {
        if (calleeRuntime == null) {
            return;
        }
        Map<String, Object> resultVars = calleeRuntime.getAllVariables();
        if (resultVar != null) {
            // Isolated scope - store result in the specified variable
            runtime.setVariable(resultVar, resultVars);
        } else {
            // Shared scope - propagate all variables back to caller
            for (Map.Entry<String, Object> entry : resultVars.entrySet()) {
                runtime.setVariable(entry.getKey(), entry.getValue());
            }
            // V1 compatibility: Also propagate config changes back to caller
            runtime.getConfig().copyFrom(calleeRuntime.getConfig());
            // V1 compatibility: Also propagate cookie jar back to caller
            runtime.getCookieJar().putAll(calleeRuntime.getCookieJar());
            // Propagate driver upward when scope is "caller"
            if (calleeRuntime.isCallerScope() && calleeRuntime.getDriverIfPresent() != null) {
                runtime.setDriverFromCallee(calleeRuntime);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeSet(Step step) {
        String text = step.getText().trim();
        Table table = step.getTable();

        if (table != null) {
            // Table-based set: * set varname | path | value |
            executeSetWithTable(text, table);
            return;
        }

        // Check for Karate-style XML xpath: "varname /xpath = value"
        // The key indicator is "space + /" pattern before the equals sign
        int eqIndex = StepUtils.findAssignmentOperator(text);
        if (eqIndex > 0) {
            String leftPart = text.substring(0, eqIndex).trim();
            // Look for "varname /xpath" pattern (space followed by /)
            int spaceSlashIdx = leftPart.indexOf(" /");
            if (spaceSlashIdx > 0) {
                String varName = leftPart.substring(0, spaceSlashIdx).trim();
                String xpath = leftPart.substring(spaceSlashIdx + 1).trim();
                String valueExpr = text.substring(eqIndex + 1).trim();

                Object target = runtime.getVariable(varName);
                if (target == null) {
                    throw new RuntimeException("variable is null or not set: " + varName);
                }
                if (!(target instanceof Node)) {
                    throw new RuntimeException("cannot set xpath on non-XML variable: " + varName);
                }

                Object value = evalValue(valueExpr);
                org.w3c.dom.Document doc = target instanceof org.w3c.dom.Document
                        ? (org.w3c.dom.Document) target
                        : ((Node) target).getOwnerDocument();
                if (value instanceof Node) {
                    Xml.setByPath(doc, xpath, (Node) value);
                } else {
                    Xml.setByPath(doc, xpath, value == null ? "" : value.toString());
                }
                return;
            }
        }

        // Default: evaluate as JS expression (e.g., "foo.b = 2", "arr[0] = 99")
        runtime.eval(text);
    }

    private Object evalValue(String valueExpr) {
        if (valueExpr.startsWith("<")) {
            // XML literal
            return Xml.toXmlDoc(valueExpr);
        }
        return runtime.eval(valueExpr);
    }

    @SuppressWarnings("unchecked")
    private void executeSetWithTable(String varExpr, Table table) {
        List<String> headers = new ArrayList<>(table.getKeys());
        List<List<String>> rows = table.getRows();

        // Determine the target variable and optional base path
        // varExpr could be: "cat", "cat.kitten", "cat $.kitten", "search /xpath"
        String varName;
        String basePath = null;
        int spaceIdx = varExpr.indexOf(' ');
        int dotIdx = varExpr.indexOf('.');
        if (spaceIdx > 0) {
            varName = varExpr.substring(0, spaceIdx);
            basePath = varExpr.substring(spaceIdx + 1).trim();
        } else if (dotIdx > 0) {
            varName = varExpr.substring(0, dotIdx);
            basePath = varExpr.substring(dotIdx + 1);
        } else {
            varName = varExpr;
        }

        // Check if this is XML XPath-based table set (basePath starts with /)
        // e.g., * set search /acc:getAccountByPhoneNumber
        //       | path | value |
        //       | acc:phoneNumber | 1234 |
        if (basePath != null && basePath.startsWith("/")) {
            executeSetXmlWithTable(varName, basePath, table);
            return;
        }

        // Check if headers indicate array construction (column headers are numbers or "path"/"value")
        boolean isPathValueFormat = headers.contains("path") && headers.contains("value");
        boolean isArrayFormat = !isPathValueFormat && headers.size() > 1;

        Object target = runtime.getVariable(varName);
        if (target == null) {
            target = isArrayFormat ? new ArrayList<>() : new LinkedHashMap<>();
            runtime.setVariable(varName, target);
        }

        // Navigate to base path if specified
        if (basePath != null && target instanceof Map) {
            String cleanPath = basePath.startsWith("$.") ? basePath.substring(2) : basePath;
            Object nested = StepUtils.getOrCreatePath((Map<String, Object>) target, cleanPath);
            target = nested;
        }

        if (isPathValueFormat) {
            // path | value format - skip header row (index 0)
            int pathIdx = headers.indexOf("path");
            int valueIdx = headers.indexOf("value");
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                String path = row.get(pathIdx);
                // Handle bounds - value column might not exist if row is shorter
                String valueExpr = valueIdx < row.size() ? row.get(valueIdx) : "";
                if (valueExpr == null || valueExpr.isEmpty()) continue;
                // V1 behavior: (expr) with parens means "keep this value even if null"
                boolean keepNull = valueExpr.startsWith("(") && valueExpr.endsWith(")");
                Object value = runtime.eval(valueExpr);
                if (value != null || keepNull) {
                    StepUtils.setValueAtPath(target, path, value);
                }
            }
        } else if (isArrayFormat) {
            // Column headers are array indices or just positional
            // path | 0 | 1 | 2 format
            int pathIdx = headers.indexOf("path");
            if (pathIdx < 0) pathIdx = 0; // first column is path

            List<Object> resultList;
            if (target instanceof List) {
                resultList = (List<Object>) target;
            } else {
                resultList = new ArrayList<>();
                runtime.setVariable(varName, resultList);
            }

            // Ensure list has enough elements
            int maxIdx = 0;
            for (int i = 0; i < headers.size(); i++) {
                if (i == pathIdx) continue;
                try {
                    int idx = Integer.parseInt(headers.get(i));
                    maxIdx = Math.max(maxIdx, idx + 1);
                } catch (NumberFormatException e) {
                    maxIdx = Math.max(maxIdx, i);
                }
            }
            while (resultList.size() < maxIdx) {
                resultList.add(new LinkedHashMap<>());
            }

            // Fill in values - skip header row (index 0)
            for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
                List<String> row = rows.get(rowIdx);
                String path = row.get(pathIdx);
                for (int i = 0; i < headers.size(); i++) {
                    if (i == pathIdx) continue;
                    // Check bounds - rows may have fewer cells than headers if trailing cells are empty
                    if (i >= row.size()) continue;
                    String valueExpr = row.get(i);
                    if (valueExpr == null || valueExpr.isEmpty()) continue;

                    int targetIdx;
                    try {
                        targetIdx = Integer.parseInt(headers.get(i));
                    } catch (NumberFormatException e) {
                        targetIdx = i - (pathIdx < i ? 1 : 0);
                    }

                    while (resultList.size() <= targetIdx) {
                        resultList.add(new LinkedHashMap<>());
                    }

                    // V1 behavior: (expr) with parens means "keep this value even if null"
                    boolean keepNull = valueExpr.startsWith("(") && valueExpr.endsWith(")");
                    Object value = runtime.eval(valueExpr);
                    if (value != null || keepNull) {
                        Object element = resultList.get(targetIdx);
                        if (element == null) {
                            element = new LinkedHashMap<>();
                            resultList.set(targetIdx, element);
                        }
                        StepUtils.setValueAtPath(element, path, value);
                    }
                }
            }
        }
    }

    /**
     * Handle set with table for XML auto-build.
     * e.g., * set search /acc:getAccountByPhoneNumber
     *       | path | value |
     *       | acc:phoneNumber | 1234 |
     * This creates XML structure from XPath paths.
     */
    private void executeSetXmlWithTable(String varName, String basePath, Table table) {
        List<String> headers = new ArrayList<>(table.getKeys());
        List<List<String>> rows = table.getRows();

        // Create or get existing XML document
        Object existing = runtime.getVariable(varName);
        org.w3c.dom.Document doc;
        if (existing instanceof org.w3c.dom.Document) {
            doc = (org.w3c.dom.Document) existing;
        } else if (existing instanceof org.w3c.dom.Node) {
            doc = ((org.w3c.dom.Node) existing).getOwnerDocument();
        } else {
            doc = Xml.newDocument();
            runtime.setVariable(varName, doc);
        }

        // Ensure the base path root element exists
        Xml.getNodeByPath(doc, basePath, true);

        // Determine column indices for path and value
        int pathIdx = headers.indexOf("path");
        int valueIdx = headers.indexOf("value");
        if (pathIdx < 0) pathIdx = 0;

        // Check if this is multi-column indexed format: | path | 1 | 2 | 3 |
        // Headers after 'path' would be numeric index values
        List<Integer> indexColumns = new ArrayList<>();
        for (int col = 0; col < headers.size(); col++) {
            if (col == pathIdx) continue;
            String header = headers.get(col);
            try {
                Integer.parseInt(header);
                indexColumns.add(col);
            } catch (NumberFormatException e) {
                // Not a numeric column
            }
        }

        if (!indexColumns.isEmpty()) {
            // Multi-column indexed format: | path | 1 | 2 | 3 |
            // Process each row (skip header row at index 0)
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.size() <= pathIdx) continue;
                String rowPath = row.get(pathIdx);

                // Process each indexed column
                for (int col : indexColumns) {
                    if (col >= row.size()) continue;
                    String valueExpr = row.get(col);
                    if (valueExpr == null || valueExpr.isEmpty()) continue;

                    // Get the index value from the header
                    String indexStr = headers.get(col);
                    // Build path: basePath[index]/rowPath
                    String fullPath = basePath + "[" + indexStr + "]/" + rowPath;

                    // Evaluate the value expression
                    Object value = evalKarateExpression(valueExpr);

                    // Set the value at the XPath
                    if (value instanceof org.w3c.dom.Node) {
                        Xml.setByPath(doc, fullPath, (org.w3c.dom.Node) value);
                    } else {
                        Xml.setByPath(doc, fullPath, value == null ? "" : value.toString());
                    }
                }
            }
        } else {
            // Standard format: | path | value |
            if (valueIdx < 0) valueIdx = 1;

            // Process each row (skip header row at index 0)
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.size() <= pathIdx) continue;
                String rowPath = row.get(pathIdx);
                String valueExpr = valueIdx < row.size() ? row.get(valueIdx) : "";
                if (valueExpr == null || valueExpr.isEmpty()) continue;

                // Build the full XPath: basePath + "/" + rowPath
                String fullPath = basePath + "/" + rowPath;

                // Evaluate the value expression (use evalKarateExpression for XML literal support)
                Object value = evalKarateExpression(valueExpr);

                // Set the value at the XPath
                if (value instanceof org.w3c.dom.Node) {
                    Xml.setByPath(doc, fullPath, (org.w3c.dom.Node) value);
                } else {
                    Xml.setByPath(doc, fullPath, value == null ? "" : value.toString());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeRemove(Step step) {
        String text = step.getText().trim();

        // Check for path syntax: "varName xpath" or "varName $jsonpath"
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex > 0) {
            String varName = text.substring(0, spaceIndex);
            String path = text.substring(spaceIndex + 1).trim();

            Object target = runtime.getVariable(varName);
            if (target == null) return;

            // Handle XPath removal - path starts with /
            if (path.startsWith("/") && target instanceof Node node) {
                org.w3c.dom.Document doc = node instanceof org.w3c.dom.Document
                        ? (org.w3c.dom.Document) node
                        : node.getOwnerDocument();
                Xml.removeByPath(doc, path);
                return;
            }

            // Handle jsonpath removal
            if (path.startsWith("$")) {
                // Extract the path after $ - e.g., "$.foo" -> "foo", "$['foo']" -> handle bracket notation
                String pathWithoutDollar = path.substring(1);
                if (pathWithoutDollar.startsWith(".")) {
                    pathWithoutDollar = pathWithoutDollar.substring(1);
                }

                // For simple paths like "foo" or "foo.bar", traverse and remove
                if (target instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) target;
                    StepUtils.removeAtPath(map, pathWithoutDollar);
                }
            }
        } else {
            // Check for dot notation: "varName.key" or just "varName"
            int dotIndex = text.indexOf('.');
            if (dotIndex < 0) {
                // Remove entire variable
                runtime.setVariable(text, null);
            } else {
                // Remove nested property - use delete in JS
                runtime.eval("delete " + text);
            }
        }
    }

    private void executeText(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        // text keyword preserves the docstring as-is (no JS evaluation)
        String value = step.getDocString();
        if (value == null) {
            value = text.substring(eqIndex + 1).trim();
        }
        runtime.setVariable(name, value);
    }

    private void executeJson(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();
        // Use evalKarateExpression to handle XML literals like: json foo = <bar>baz</bar>
        Object value = evalKarateExpression(expr);
        // Convert to JSON map/object
        if (value instanceof Node) {
            value = Xml.toObject((Node) value);
        } else if (value instanceof String s) {
            // Parse string as JSON: json foo = strVar where strVar = '{ "foo": "bar" }'
            value = Json.of(s).value();
        } else if (value instanceof Map || value instanceof List) {
            // Already JSON-like, keep as is
        } else if (value != null) {
            // POJO or other Java object - convert using Jackson
            value = Json.of(value).value();
        }
        value = processEmbeddedExpressions(value);
        runtime.setVariable(name, value);
    }

    @SuppressWarnings("unchecked")
    private void executeXml(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String expr;
        if (step.getDocString() != null) {
            expr = step.getDocString();
        } else {
            expr = text.substring(eqIndex + 1).trim();
        }
        // Evaluate expression using Karate expression evaluation (handles $var /xpath, etc.)
        Object value = evalKarateExpression(expr);
        if (value instanceof String) {
            value = Xml.toXmlDoc((String) value);
        } else if (value instanceof Node) {
            // Already XML, keep as is
        } else if (value instanceof Map) {
            // JSON/Map to XML
            value = Xml.fromMap((Map<String, Object>) value);
        } else if (value != null) {
            // POJO or other Java object - convert to JSON first, then wrap in root element
            Object jsonValue = Json.of(value).value();
            value = Xml.fromObject("root", jsonValue);
        }
        runtime.setVariable(name, value);
    }

    private void executeXmlString(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();
        Object value = runtime.eval(expr);
        // Convert to XML string representation
        String xmlString;
        if (value instanceof Node) {
            xmlString = Xml.toString((Node) value, false);
        } else {
            xmlString = value.toString();
        }
        runtime.setVariable(name, xmlString);
    }

    private void executeString(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();
        Object value = runtime.eval(expr);
        // Process embedded expressions like #(foo)
        value = processEmbeddedExpressions(value);
        // Convert to string representation - XML uses Xml.toString(), others use JSON
        String stringValue;
        if (value instanceof Node) {
            stringValue = Xml.toString((Node) value, false);
        } else {
            stringValue = Json.stringifyStrict(value);
        }
        runtime.setVariable(name, stringValue);
    }

    private void executeCsv(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String csvText;
        if (step.getDocString() != null) {
            // Doc string: csv data = """..."""
            csvText = step.getDocString();
        } else {
            // Expression: csv data = someVar
            String expr = text.substring(eqIndex + 1).trim();
            Object value = runtime.eval(expr);
            csvText = value.toString();
        }
        List<Map<String, Object>> result = DataUtils.fromCsv(csvText);
        runtime.setVariable(name, result);
    }

    private void executeYaml(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String yamlText;
        if (step.getDocString() != null) {
            // Doc string: yaml data = """..."""
            yamlText = step.getDocString();
        } else {
            // Expression: yaml data = someVar
            String expr = text.substring(eqIndex + 1).trim();
            Object value = runtime.eval(expr);
            yamlText = value.toString();
        }
        Object result = DataUtils.fromYaml(yamlText);
        runtime.setVariable(name, result);
    }

    private void executeCopy(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();
        Object value = runtime.eval(expr);
        // Deep copy using JSON round-trip
        Object copy = Json.of(Json.stringifyStrict(value)).value();
        runtime.setVariable(name, copy);
    }

    private void executeTable(Step step) {
        String text = step.getText().trim();
        Table table = step.getTable();
        if (table == null) {
            throw new RuntimeException("table keyword requires a data table");
        }
        // Get raw string values, then evaluate each as a Karate expression (V1 behavior)
        List<Map<String, String>> rawRows = table.getRowsAsMaps();
        List<Map<String, Object>> result = new ArrayList<>(rawRows.size());
        for (Map<String, String> rawRow : rawRows) {
            Map<String, Object> row = new LinkedHashMap<>(rawRow.size());
            for (Map.Entry<String, String> entry : rawRow.entrySet()) {
                String expr = entry.getValue();
                if (expr == null || expr.isEmpty()) {
                    // Skip empty values (V1 strips these by default)
                    continue;
                }
                // V1 behavior: (expr) with parens means "keep this value even if null"
                // Without parens, null values are skipped
                boolean keepNull = expr.startsWith("(") && expr.endsWith(")");
                Object value = runtime.eval(expr);
                if (value != null || keepNull) {
                    row.put(entry.getKey(), value);
                }
            }
            result.add(row);
        }
        runtime.setVariable(text, result);
    }

    private void executeReplace(Step step) {
        String text = step.getText();
        Table table = step.getTable();

        if (table != null) {
            // Table syntax: replace varName
            //   | token | value   |
            //   | one   | 'cruel' |
            String varName = text.trim();
            Object varValue = runtime.getVariable(varName);
            if (varValue == null) {
                throw new RuntimeException("no variable found with name: " + varName);
            }
            String varText;
            if (varValue instanceof Node) {
                varText = Xml.toString((Node) varValue, false);
            } else if (varValue instanceof Map || varValue instanceof List) {
                varText = Json.stringifyStrict(varValue);
            } else {
                varText = varValue.toString();
            }

            // Process each row in the table
            List<Map<String, String>> rows = table.getRowsAsMaps();
            for (Map<String, String> row : rows) {
                String token = row.get("token");
                String valueExpr = row.get("value");

                // Evaluate the replacement expression
                Object replaceValue = runtime.eval(valueExpr);
                String replacement = replaceValue != null ? replaceValue.toString() : "";

                // If token is alphanumeric, wrap with < >
                if (token != null && !token.isEmpty() && Character.isLetterOrDigit(token.charAt(0))) {
                    token = '<' + token + '>';
                }

                // Perform replacement
                if (token != null) {
                    varText = varText.replace(token, replacement);
                }
            }
            runtime.setVariable(varName, varText);
        } else {
            // Single-line syntax: replace varName.token = 'replacement'
            int eqIndex = StepUtils.findAssignmentOperator(text);
            if (eqIndex < 0) {
                throw new RuntimeException("replace requires '=' assignment: " + text);
            }
            String nameAndToken = text.substring(0, eqIndex).trim();
            String replaceExpr = text.substring(eqIndex + 1).trim();

            // Parse varName.token
            int dotIndex = nameAndToken.indexOf('.');
            if (dotIndex < 0) {
                throw new RuntimeException("replace requires varName.token syntax: " + nameAndToken);
            }
            String varName = nameAndToken.substring(0, dotIndex).trim();
            String token = nameAndToken.substring(dotIndex + 1).trim();

            // Get the variable value as string
            Object varValue = runtime.getVariable(varName);
            if (varValue == null) {
                throw new RuntimeException("no variable found with name: " + varName);
            }
            String varText;
            if (varValue instanceof Node) {
                varText = Xml.toString((Node) varValue, false);
            } else if (varValue instanceof Map || varValue instanceof List) {
                varText = Json.stringifyStrict(varValue);
            } else {
                varText = varValue.toString();
            }

            // Evaluate the replacement expression
            Object replaceValue = runtime.eval(replaceExpr);
            String replacement = replaceValue != null ? replaceValue.toString() : "";

            // If token is alphanumeric, wrap with < >
            if (!token.isEmpty() && Character.isLetterOrDigit(token.charAt(0))) {
                token = '<' + token + '>';
            }

            // Perform replacement and update variable
            String replaced = varText.replace(token, replacement);
            runtime.setVariable(varName, replaced);
        }
    }

    // ========== Assertions ==========

    private void executeMatch(Step step) {
        String text = step.getText();
        MatchExpression expr = GherkinParser.parseMatchExpression(text);

        Object actual = evalMatchExpression(expr.getActualExpr());

        // Check for docstring as expected value (e.g., match foo contains deep """ {...} """)
        Object expected;
        String docString = step.getDocString();
        if (docString != null && (expr.getExpectedExpr() == null || expr.getExpectedExpr().isEmpty())) {
            // DocString provides the expected value
            expected = evalMatchExpression(docString);
        } else {
            expected = evalMatchExpression(expr.getExpectedExpr());
        }

        Match.Type matchType = Match.Type.valueOf(expr.getMatchTypeName());

        boolean matchEachEmptyAllowed = runtime.getConfig().isMatchEachEmptyAllowed();
        Result result = Match.execute(runtime.getEngine(), matchType, actual, expected, matchEachEmptyAllowed);
        if (!result.pass) {
            String message = result.message;
            // Include comment as assertion label if present
            List<String> comments = step.getComments();
            if (comments != null && !comments.isEmpty()) {
                // Use the last comment as the label (closest to the step)
                String label = comments.getLast();
                message = label + "\n" + message;
            }
            throw new AssertionError(message);
        }
    }

    /**
     * Evaluates expressions in match statements, handling both JS and jsonpath.
     * Detects jsonpath patterns like var[*].path, $var[*].path, and uses
     * jsonpath evaluation for those instead of JS.
     */
    private Object evalMatchExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }

        // Handle XML literal - expression starting with <
        if (expr.startsWith("<")) {
            return Xml.toXmlDoc(expr);
        }

        // Handle JSON literal - expression starting with { or [
        // V1 behavior: parse as JSON first, then evaluate embedded expressions
        if (StringUtils.looksLikeJson(expr)) {
            try {
                Object value = Json.of(expr).value();
                // Process embedded expressions like #(varName) in the parsed JSON
                return processEmbeddedExpressions(value);
            } catch (Exception e) {
                // Fall through to JS eval if JSON parsing fails
            }
        }

        // Handle "header <name>" syntax for accessing response headers (V1 compatibility)
        // e.g., "match header Content-Type contains 'application/json'"
        if (expr.startsWith("header ")) {
            String headerName = expr.substring(7).trim();
            return getResponseHeader(headerName);
        }

        // Handle get[N] or get syntax (same as in def)
        if (expr.startsWith("get[") || expr.startsWith("get ")) {
            return evalGetExpression(expr);
        }

        // Handle $varname patterns - could be jsonpath OR xpath on XML variable
        // $varname/xpath, $varname /xpath, $[...], $., $varname[*].path
        if (expr.startsWith("$")) {
            return evalDollarPrefixedExpression(expr);
        }

        // Handle bare xpath on response: "//xpath" or "/xpath"
        // e.g., "match //myelement == 'foo'" operates on response variable
        if (expr.startsWith("/")) {
            Object response = runtime.getVariable("response");
            if (response instanceof Node) {
                return KarateJsUtils.evalXmlPath((Node) response, expr);
            }
        }

        // Handle "varname /" syntax - XPath root shortcut for XML variable
        // e.g., "temp /" means the root of the XML document stored in 'temp'
        if (expr.endsWith(" /") || expr.equals("/")) {
            String varName = expr.endsWith(" /") ? expr.substring(0, expr.length() - 2).trim() : null;
            if (varName != null) {
                Object target = runtime.getVariable(varName);
                if (target instanceof Node) {
                    return target; // Return the XML node directly
                }
            }
        }

        // Handle XPath patterns with or without space:
        // - "varname /xpath/path" (with space)
        // - "varname/xpath/path" (no space) - V1 compatibility
        // - "varname //xpath" (double-slash xpath)
        Object xpathResult = tryEvalXPathExpression(expr);
        if (xpathResult != null) {
            // If XPath was evaluated but path not found, return #notpresent
            if (xpathResult == XPATH_NOT_PRESENT) {
                return "#notpresent";
            }
            return xpathResult;
        }

        // Handle XPath function syntax: "varname function(/xpath)"
        // e.g., "foo count(/records//record)" or "xml //record[@index=2]"
        int spaceIdx = expr.indexOf(' ');
        if (spaceIdx > 0) {
            String varName = expr.substring(0, spaceIdx);
            String remainder = expr.substring(spaceIdx + 1).trim();
            // Check if remainder is XPath (starts with /) or XPath function (like count(...))
            if (remainder.startsWith("/") || isXPathFunction(remainder)) {
                Object target = runtime.getVariable(varName);
                if (target instanceof Node) {
                    return evalXmlPathWithFunction((Node) target, remainder);
                }
            }
        }

        // Check if expression contains jsonpath wildcard [*], filter [?(...)] or deep-scan (..)
        if (expr.contains("[*]") || expr.contains("[?") || expr.contains("..")) {
            // Check if it's var[*].path, var[?...].path, var..path, or var.prop[*].path pattern
            int bracketIdx = expr.indexOf('[');
            int doubleDotIdx = expr.indexOf("..");
            int splitIdx = bracketIdx > 0 ? bracketIdx : doubleDotIdx;
            if (splitIdx > 0) {
                String basePath = expr.substring(0, splitIdx);
                // Verify it starts with a valid identifier
                int firstDot = basePath.indexOf('.');
                String varName = firstDot > 0 ? basePath.substring(0, firstDot) : basePath;
                if (varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    Object target;
                    if (firstDot > 0) {
                        // Evaluate dotted path (e.g., "response.kittens") via JS
                        target = runtime.eval(basePath);
                    } else {
                        target = runtime.getVariable(varName);
                    }
                    if (target != null) {
                        String jsonPath = "$" + expr.substring(splitIdx);
                        return JsonPath.read(target, jsonPath);
                    }
                }
            }
        }

        // Handle "varname $jsonpath" pattern for JSONPath on a variable
        // e.g., "jsonVar $.root.foo._" means apply JSONPath "$.root.foo._" to jsonVar
        int dollarIdx = expr.indexOf(" $");
        if (dollarIdx > 0) {
            String varName = expr.substring(0, dollarIdx).trim();
            String jsonPath = expr.substring(dollarIdx + 1).trim();
            if (StepUtils.isValidVariableName(varName) && jsonPath.startsWith("$")) {
                Object target = runtime.getVariable(varName);
                if (target != null) {
                    return JsonPath.read(target, jsonPath);
                }
            }
        }

        // Default: evaluate as JS
        Object result = runtime.eval(expr);

        // Check for "not present" case: if result is null and expression is property access
        // We need to distinguish between "property exists and is null" vs "property doesn't exist"
        if (result == null && expr.contains(".") && !expr.contains("(")) {
            // Check if it's a simple property access like "foo.bar"
            int lastDot = expr.lastIndexOf('.');
            if (lastDot > 0) {
                String basePath = expr.substring(0, lastDot);
                String propName = expr.substring(lastDot + 1);
                // Use JS to check if property exists
                try {
                    Object exists = runtime.eval("typeof " + basePath + " !== 'undefined' && " + basePath + " !== null && '" + propName + "' in " + basePath);
                    if (Boolean.FALSE.equals(exists)) {
                        return "#notpresent";
                    }
                } catch (Exception e) {
                    // If check fails, treat as not present
                    return "#notpresent";
                }
            }
        }
        return result;
    }

    /** Sentinel value indicating XPath was evaluated but returned no result. */
    private static final Object XPATH_NOT_PRESENT = new Object();

    /**
     * Tries to evaluate an expression as XPath on an XML variable.
     * Handles patterns like:
     * - "varname /xpath" (with space)
     * - "varname/xpath" (no space, V1 compatibility)
     * - "varname //xpath" (double-slash xpath)
     *
     * @return the XPath result, XPATH_NOT_PRESENT if path not found, or null if expression is not an XPath pattern
     */
    private Object tryEvalXPathExpression(String expr) {
        // Pattern 1: "varname /xpath" or "varname //xpath" (with space)
        int spaceSlashIdx = expr.indexOf(" /");
        if (spaceSlashIdx > 0) {
            String varName = expr.substring(0, spaceSlashIdx).trim();
            String xpath = expr.substring(spaceSlashIdx + 1).trim();
            if (StepUtils.isValidVariableName(varName)) {
                Object target = runtime.getVariable(varName);
                if (target instanceof Node) {
                    Object result = KarateJsUtils.evalXmlPath((Node) target, xpath);
                    return result == null ? XPATH_NOT_PRESENT : result;
                }
            }
        }

        // Pattern 2: "varname/xpath" (no space, V1 compatibility)
        // Find first / that's not preceded by space
        int slashIdx = expr.indexOf('/');
        if (slashIdx > 0 && (spaceSlashIdx < 0 || slashIdx < spaceSlashIdx)) {
            // Make sure the character before / is not a space
            if (expr.charAt(slashIdx - 1) != ' ') {
                String varName = expr.substring(0, slashIdx);
                if (StepUtils.isValidVariableName(varName)) {
                    Object target = runtime.getVariable(varName);
                    if (target instanceof Node) {
                        String xpath = expr.substring(slashIdx);
                        Object result = KarateJsUtils.evalXmlPath((Node) target, xpath);
                        return result == null ? XPATH_NOT_PRESENT : result;
                    }
                }
            }
        }

        return null; // Not an XPath expression
    }

    /**
     * Checks if text is an XPath function like count(), sum(), etc.
     * Pattern: starts with lowercase letters followed by opening paren.
     */
    private boolean isXPathFunction(String text) {
        return text.matches("^[a-z-]+\\(.+");
    }

    /**
     * Evaluates XPath or XPath function on an XML node.
     * Handles functions like count() that return values instead of nodes.
     */
    private Object evalXmlPathWithFunction(Node node, String path) {
        // Try to evaluate as normal XPath returning nodes
        try {
            Object result = KarateJsUtils.evalXmlPath(node, path);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            // XPath functions like count() throw exceptions when trying to get NodeList
        }

        // For XPath functions that don't return nodes (e.g., count, sum, string-length),
        // evaluate as text value
        String textValue = Xml.getTextValueByPath(node, path);
        if (textValue != null && !textValue.isEmpty()) {
            // Special case: count() returns an integer
            if (path.startsWith("count(") || path.startsWith("count ")) {
                try {
                    return Integer.parseInt(textValue.split("\\.")[0]); // Handle "3.0" -> 3
                } catch (NumberFormatException e) {
                    return textValue;
                }
            }
            // Try to return as number if applicable
            try {
                if (textValue.contains(".")) {
                    return Double.parseDouble(textValue);
                }
                return Integer.parseInt(textValue);
            } catch (NumberFormatException e) {
                return textValue;
            }
        }
        return null;
    }

    /**
     * Handles $varname prefixed expressions.
     * Could be:
     * - $varname/xpath or $varname /xpath - XPath on XML variable
     * - $varname//xpath - double-slash XPath
     * - $[...] or $. - jsonpath on response
     * - $varname[*].path - jsonpath on variable
     */
    private Object evalDollarPrefixedExpression(String expr) {
        String withoutDollar = expr.substring(1);

        // Special case: bare $ means 'response' variable
        if (withoutDollar.isEmpty()) {
            return runtime.getVariable("response");
        }

        // Special case: $[...] or $. means use 'response' variable for jsonpath
        if (withoutDollar.startsWith("[") || withoutDollar.startsWith(".")) {
            Object target = runtime.getVariable("response");
            if (target instanceof Node) {
                // Response is XML - shouldn't use jsonpath
                return target;
            }
            String path = "$" + withoutDollar;
            return JsonPath.read(toJsonForJsonPath(target), path);
        }

        // Find where the path starts (at space+/, /, ., or [)
        int spaceSlashIdx = withoutDollar.indexOf(" /");
        int slashIdx = withoutDollar.indexOf('/');
        int dotIdx = withoutDollar.indexOf('.');
        int bracketIdx = withoutDollar.indexOf('[');

        // Determine variable name and path type
        String varName;
        String path;
        boolean isXPath = false;

        // Check for XPath patterns first: $varname /xpath or $varname/xpath
        if (spaceSlashIdx > 0) {
            // $varname /xpath (with space)
            varName = withoutDollar.substring(0, spaceSlashIdx);
            path = withoutDollar.substring(spaceSlashIdx + 1).trim();
            isXPath = true;
        } else if (slashIdx > 0 && (dotIdx < 0 || slashIdx < dotIdx) && (bracketIdx < 0 || slashIdx < bracketIdx)) {
            // $varname/xpath (no space)
            varName = withoutDollar.substring(0, slashIdx);
            path = withoutDollar.substring(slashIdx);
            isXPath = true;
        } else if (dotIdx > 0 && (bracketIdx < 0 || dotIdx < bracketIdx)) {
            // $varname.path - jsonpath
            varName = withoutDollar.substring(0, dotIdx);
            path = "$" + withoutDollar.substring(dotIdx);
        } else if (bracketIdx > 0) {
            // $varname[...] - jsonpath
            varName = withoutDollar.substring(0, bracketIdx);
            path = "$" + withoutDollar.substring(bracketIdx);
        } else {
            // Just $varname - return the variable itself
            return runtime.getVariable(withoutDollar);
        }

        Object target = runtime.getVariable(varName);
        if (target == null) {
            return null;
        }

        if (isXPath) {
            if (target instanceof Node) {
                return KarateJsUtils.evalXmlPath((Node) target, path);
            }
            // Fall back to jsonpath if not XML
            return JsonPath.read(toJsonForJsonPath(target), "$" + path);
        } else {
            // JsonPath
            return JsonPath.read(toJsonForJsonPath(target), path);
        }
    }

    /**
     * Converts a String to JSON (Map/List) if it looks like JSON, for JSONPath evaluation.
     * V1 compatibility: allows JSONPath on strings that contain JSON-like content.
     */
    private Object toJsonForJsonPath(Object value) {
        if (value instanceof String s) {
            if (StringUtils.looksLikeJson(s)) {
                try {
                    // Use JS engine to parse - handles both JSON and JS object literals
                    return runtime.eval("(" + s + ")");
                } catch (Exception e) {
                    // If parsing fails, return the original string
                    return value;
                }
            }
        }
        return value;
    }

    /**
     * Validates a variable name and throws if invalid.
     */
    private void validateVariableName(String name) {
        if (!StepUtils.isValidVariableName(name)) {
            throw new RuntimeException("invalid variable name: " + name);
        }
        if ("karate".equals(name)) {
            throw new RuntimeException("'karate' is a reserved name");
        }
    }

    /**
     * Evaluates a Karate expression, handling:
     * - $varname/xpath, $varname[*].path (jsonpath)
     * - get[N] varname path
     * - <xml> literals
     * - varname/xpath (XML XPath shorthand)
     * - Regular JS expressions with embedded expression processing
     */
    private Object evalKarateExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return null;
        }

        expr = expr.trim();

        // Handle call/callonce expressions (V1 compatibility for RHS usage like: header X = call fun {...})
        if (expr.startsWith("call ")) {
            return evalCallExpression(expr.substring(5).trim());
        }
        if (expr.startsWith("callonce ")) {
            return evalCallOnceExpression(expr.substring(9).trim());
        }

        // Handle $varname patterns - could be jsonpath OR xpath on XML variable
        if (expr.startsWith("$")) {
            return evalDollarPrefixedExpression(expr);
        }

        // Handle get[N] or get syntax
        if (expr.startsWith("get[") || expr.startsWith("get ")) {
            return evalGetExpression(expr);
        }

        // Handle XML literal - expression starting with <
        if (expr.startsWith("<")) {
            Object value = Xml.toXmlDoc(expr);
            return processEmbeddedExpressions(value);
        }

        // Handle bare xpath on response: "//xpath" or "/xpath"
        if (expr.startsWith("/")) {
            Object response = runtime.getVariable("response");
            if (response instanceof Node) {
                return KarateJsUtils.evalXmlPath((Node) response, expr);
            }
        }

        // Handle XPath patterns: "varname/xpath" or "varname /xpath"
        Object xpathResult = tryEvalXPathExpression(expr);
        if (xpathResult != null) {
            // For def/assignment: if XPath returned not present, use null
            return xpathResult == XPATH_NOT_PRESENT ? null : xpathResult;
        }

        // Default: evaluate as JS
        // Only process embedded expressions if original expression is a JSON/JS object literal
        boolean isLiteral = expr.startsWith("{") || expr.startsWith("[");
        Object value = runtime.eval(expr);
        if (isLiteral && (value instanceof Map || value instanceof List)) {
            value = processEmbeddedExpressions(value);
        }
        return value;
    }

    private void executeAssert(Step step) {
        Object result = runtime.eval(step.getText());
        if (result instanceof Boolean b) {
            if (!b) {
                throw new AssertionError("assert failed: " + step.getText());
            }
        } else {
            throw new RuntimeException("assert expression must return boolean: " + step.getText());
        }
    }

    private void executePrint(Step step) {
        // Wrap in array to handle comma-separated expressions like: print 'foo', 'bar'
        // Without wrapping, JS comma operator would return only the last value
        Object value = runtime.eval("[" + step.getText() + "]");
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(StepUtils.stringify(list.get(i)));
            }
            LogContext.get().log(sb.toString());
        } else {
            LogContext.get().log(StepUtils.stringify(value));
        }
    }

    // ========== HTTP ==========

    private HttpRequestBuilder http() {
        return runtime.getHttp();
    }

    private void applyCookiesFromMap(Map<?, ?> cookieMap) {
        for (Map.Entry<?, ?> entry : cookieMap.entrySet()) {
            http().cookie(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    private AuthHandler buildAuthHandler(KarateConfig config) {
        Map<String, Object> auth = config.getAuth();
        if (auth == null || auth.isEmpty()) {
            return null;
        }
        String type = (String) auth.get("type");
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "basic" -> new BasicAuthHandler(
                    (String) auth.get("username"),
                    (String) auth.get("password")
            );
            case "bearer" -> new BearerAuthHandler(
                    (String) auth.get("token")
            );
            case "oauth2" -> {
                Map<String, Object> oauth2Config = new HashMap<>();
                oauth2Config.put("url", auth.get("accessTokenUrl"));
                oauth2Config.put("client_id", auth.get("clientId"));
                oauth2Config.put("client_secret", auth.get("clientSecret"));
                if (auth.containsKey("scope")) {
                    oauth2Config.put("scope", auth.get("scope"));
                }
                yield new ClientCredentialsAuthHandler(oauth2Config);
            }
            default -> null;
        };
    }

    private void executeUrl(Step step) {
        String urlExpr = step.getText();
        String url = (String) runtime.eval(urlExpr);
        http().url(url);
    }

    private void executePath(Step step) {
        String pathExpr = step.getText().trim();
        // If expression contains comma, wrap in array to handle multiple segments
        // e.g., path 'users', '123' becomes ['users', '123']
        Object path;
        if (pathExpr.contains(",")) {
            path = runtime.eval("[" + pathExpr + "]");
        } else {
            path = runtime.eval(pathExpr);
        }
        if (path instanceof List<?> list) {
            for (Object item : list) {
                http().path(item.toString());
            }
        } else {
            http().path(path.toString());
        }
    }

    private void executeParam(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        Object value = runtime.eval(text.substring(eqIndex + 1).trim());
        if (value instanceof List<?> list) {
            for (Object item : list) {
                http().param(name, item.toString());
            }
        } else {
            http().param(name, value.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void executeParams(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = entry.getKey().toString();
                Object v = entry.getValue();
                // V1 behavior: skip null values
                if (v == null) {
                    continue;
                }
                if (v instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) {
                            http().param(name, item.toString());
                        }
                    }
                } else {
                    http().param(name, v.toString());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeHeader(Step step) {
        Table table = step.getTable();
        if (table != null) {
            for (Map<String, String> row : table.getRowsAsMaps()) {
                String name = row.get("name");
                String value = row.get("value");
                http().header(name, value);
            }
        } else {
            String text = step.getText();
            int eqIndex = StepUtils.findAssignmentOperator(text);
            String name = text.substring(0, eqIndex).trim();
            // Use evalKarateExpression to handle call expressions like: header X = call fun {...}
            Object value = evalKarateExpression(text.substring(eqIndex + 1).trim());
            // V1 compatibility: function calls can return arrays for multi-value headers
            if (value instanceof List) {
                List<String> list = ((List<?>) value).stream()
                        .map(Object::toString)
                        .toList();
                http().header(name, list);
            } else {
                http().header(name, value.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeHeaders(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof Map<?, ?> map) {
            http().headers((Map<String, Object>) map);
        }
    }

    @SuppressWarnings("unchecked")
    private void executeCookie(Step step) {
        // Cookies are accumulated in HttpRequestBuilder and combined when request is built
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        Object value = runtime.eval(text.substring(eqIndex + 1).trim());
        if (value instanceof Map) {
            // V1 behavior: cookie foo = { value: 'bar', domain: '.abc.com' }
            Map<String, Object> map = (Map<String, Object>) value;
            map.put("name", name);
            http().cookie(map);
        } else {
            http().cookie(name, value.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void executeCookies(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                http().cookie(entry.getKey().toString(), entry.getValue().toString());
            }
        }
    }

    private void executeFormField(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        String name = text.substring(0, eqIndex).trim();
        Object value = runtime.eval(text.substring(eqIndex + 1).trim());
        http().formField(name, value.toString());
    }

    @SuppressWarnings("unchecked")
    private void executeFormFields(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                http().formField(entry.getKey().toString(), entry.getValue().toString());
            }
        }
    }

    private void executeRequest(Step step) {
        String expr = step.getDocString() != null ? step.getDocString() : step.getText();
        Object body = evalKarateExpression(expr);
        http().body(body);
    }

    private void executeRetryUntil(Step step) {
        String condition = step.getText().trim();
        http().retryUntil(condition);
    }

    private void executeSoapAction(Step step) {
        String text = step.getText();
        String action = "";
        if (text != null && !text.isBlank()) {
            Object result = runtime.eval(text.trim());
            action = result != null ? result.toString() : "";
        }
        http().header("SOAPAction", action);
        http().contentType("text/xml");
        doMethod("POST");
    }

    private void executeMethod(Step step) {
        String method = step.getText().trim().toUpperCase();
        doMethod(method);
    }

    @SuppressWarnings("unchecked")
    private void doMethod(String method) {
        KarateConfig config = runtime.getConfig();

        // Apply cookies from the cookie jar (V1 compatibility: auto-send responseCookies)
        Map<String, Map<String, Object>> cookieJar = runtime.getCookieJar();
        for (Map.Entry<String, Map<String, Object>> entry : cookieJar.entrySet()) {
            String cookieName = entry.getKey();
            Map<String, Object> cookieData = entry.getValue();
            Object value = cookieData.get("value");
            if (value != null) {
                http().cookie(cookieName, value.toString());
            }
        }

        // Apply configured cookies before invoking request - may be a Map or a JsCallable
        Object configCookies = config.getCookies();
        if (configCookies instanceof JavaCallable cookiesFn) {
            // Call function to get cookies dynamically
            Object result = cookiesFn.call(null);
            if (result instanceof Map<?, ?> cookieMap) {
                applyCookiesFromMap(cookieMap);
            }
        } else if (configCookies instanceof Map<?, ?> cookieMap) {
            applyCookiesFromMap(cookieMap);
        }

        // Apply configured headers - may be a Map or a JsCallable
        Object configHeaders = config.getHeaders();
        if (configHeaders instanceof JavaCallable headersFn) {
            // Build request first so function can access current state (for signing etc.)
            HttpRequest request = http().build();
            Object result = headersFn.call(null, request);
            if (result instanceof Map<?, ?> headersMap) {
                http().headers((Map<String, Object>) headersMap);
            }
        } else if (configHeaders instanceof Map<?, ?> headersMap) {
            http().headers((Map<String, Object>) headersMap);
        }

        // Apply configured auth handler
        AuthHandler authHandler = buildAuthHandler(config);
        if (authHandler != null) {
            http().auth(authHandler);
        }

        // Get perf event name before request (if in perf mode)
        String perfEventName = null;
        if (runtime.isPerfMode()) {
            // Set method on builder before building so request.getMethod() works
            http().method(method);
            HttpRequest builtRequest = http().build();
            PerfHook perfHook = runtime.getPerfHook();
            if (perfHook != null) {
                perfEventName = perfHook.getPerfEventName(builtRequest, runtime);
            }
        }

        // Check for retry condition
        String retryUntil = http().getRetryUntil();
        HttpResponse response;
        if (retryUntil != null) {
            // Retry logic handles its own HTTP events per attempt
            response = executeMethodWithRetry(method, retryUntil, config);
        } else {
            // Build request for HTTP_ENTER event
            http().method(method);
            HttpRequest request = http().build();

            // Fire HTTP_ENTER event - listener can return false to skip
            Suite suite = getSuite();
            boolean shouldProceed = true;
            if (suite != null) {
                shouldProceed = suite.fireEvent(HttpRunEvent.enter(request, runtime));
            }

            if (shouldProceed) {
                response = http().invoke(method);
            } else {
                // Request skipped by listener
                response = HttpResponse.skipped(request);
            }

            // Fire HTTP_EXIT event (always, even for skipped)
            if (suite != null) {
                suite.fireEvent(HttpRunEvent.exit(response.getRequest(), response, runtime));
            }
        }

        // Track previous request for karate.prevRequest
        if (response.getRequest() != null) {
            runtime.getKarate().setPrevRequest(response.getRequest());
        }

        setResponseVariables(response);

        // Capture perf event (if in perf mode)
        if (perfEventName != null) {
            PerfEvent perfEvent = new PerfEvent(
                    response.getStartTime(),
                    response.getStartTime() + response.getResponseTime(),
                    perfEventName,
                    response.getStatus()
            );
            runtime.capturePerfEvent(perfEvent);
        }

        // Log HTTP request/response to context
        LogContext ctx = LogContext.get();
        if (response.getRequest() != null) {
            ctx.log("{} {}", method, response.getRequest().getUrlAndPath());
        }
        ctx.log("{} ({} ms)", response.getStatus(), response.getResponseTime());
    }

    private void setResponseVariables(HttpResponse response) {
        Object body = response.getBodyConverted();
        runtime.setVariable("response", body);
        runtime.setVariable("responseStatus", response.getStatus());
        runtime.setVariable("responseHeaders", response.getHeaders());
        runtime.setVariable("responseTime", response.getResponseTime());
        // Hidden variables (accessible but not in getAllVariables())
        runtime.setHiddenVariable("responseBytes", response.getBodyBytes());
        Object responseCookies = response.getCookies();
        runtime.setHiddenVariable("responseCookies", responseCookies);
        runtime.setHiddenVariable("requestTimeStamp", response.getStartTime());

        // Update cookie jar with responseCookies for auto-send on subsequent requests (V1 compatibility)
        runtime.updateCookieJar(responseCookies);

        // Determine response type for V1 compatibility
        String responseType;
        if (body instanceof byte[]) {
            responseType = "binary";
        } else if (body instanceof Map || body instanceof List) {
            responseType = "json";
        } else if (body instanceof org.w3c.dom.Node) {
            responseType = "xml";
        } else {
            responseType = "string";
        }
        runtime.setHiddenVariable("responseType", responseType);
    }

    private HttpResponse executeMethodWithRetry(String method, String retryUntil, KarateConfig config) {
        int maxRetries = config.getRetryCount();
        int sleepInterval = config.getRetryInterval();
        int retryCount = 0;
        Suite suite = getSuite();

        while (true) {
            if (retryCount == maxRetries) {
                throw new RuntimeException("retry failed after " + maxRetries + " attempts: " + retryUntil);
            }
            if (retryCount > 0) {
                try {
                    logger.debug("sleeping {} ms before retry #{}", sleepInterval, retryCount);
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("retry interrupted", e);
                }
            }

            // Make a copy of the request builder to preserve state for retry
            HttpRequestBuilder httpCopy = http().copy();

            // Build request for HTTP_ENTER event
            http().method(method);
            HttpRequest request = http().build();

            // Fire HTTP_ENTER event - listener can return false to skip
            boolean shouldProceed = true;
            if (suite != null) {
                shouldProceed = suite.fireEvent(HttpRunEvent.enter(request, runtime));
            }

            HttpResponse response;
            if (shouldProceed) {
                response = http().invoke(method);
            } else {
                // Request skipped by listener
                response = HttpResponse.skipped(request);
            }

            // Fire HTTP_EXIT event (always, even for skipped)
            if (suite != null) {
                suite.fireEvent(HttpRunEvent.exit(response.getRequest(), response, runtime));
            }

            // Set response variables so the condition can access them
            setResponseVariables(response);

            // Evaluate retry condition
            boolean conditionMet;
            try {
                Object result = runtime.eval(retryUntil);
                conditionMet = Boolean.TRUE.equals(result);
            } catch (Exception e) {
                logger.warn("retry condition evaluation failed: {}", e.getMessage());
                conditionMet = false;
            }

            if (conditionMet) {
                if (retryCount > 0) {
                    logger.debug("retry condition satisfied after {} attempts", retryCount + 1);
                }
                return response;
            } else {
                logger.debug("retry condition not satisfied: {}", retryUntil);
            }

            // Restore request state for next retry (http().invoke() resets it)
            http().restoreFrom(httpCopy);

            retryCount++;
        }
    }

    private void executeStatus(Step step) {
        int expected = Integer.parseInt(step.getText().trim());
        Object statusObj = runtime.getVariable("responseStatus");
        int actual = statusObj instanceof Number n ? n.intValue() : Integer.parseInt(statusObj.toString());
        if (actual != expected) {
            throw new AssertionError("expected status: " + expected + ", actual: " + actual);
        }
    }

    /**
     * Handles: multipart file myFile = { read: 'file.txt', filename: 'test.txt', contentType: 'text/plain' }
     * Or shorthand: multipart file myFile = read('file.txt')
     */
    @SuppressWarnings("unchecked")
    private void executeMultipartFile(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        if (eqIndex < 0) {
            throw new RuntimeException("multipart file requires '=' assignment: " + text);
        }
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();

        Object value = evalKarateExpression(expr);

        Map<String, Object> multipartMap = new HashMap<>();
        multipartMap.put("name", name);

        if (value instanceof Map) {
            Map<String, Object> fileMap = (Map<String, Object>) value;
            // Handle { read: 'path', filename: 'name', contentType: 'type' }
            Object readPath = fileMap.get("read");
            if (readPath != null) {
                Resource resource = resolveResource(readPath.toString());
                File file = getFileFromResource(resource);
                if (file != null) {
                    multipartMap.put("value", file);
                } else {
                    // For classpath or in-memory resources, read bytes
                    try (InputStream is = resource.getStream()) {
                        byte[] bytes = is.readAllBytes();
                        multipartMap.put("value", bytes);
                    } catch (Exception e) {
                        throw new RuntimeException("failed to read file: " + readPath, e);
                    }
                }
            } else if (fileMap.get("value") != null) {
                multipartMap.put("value", fileMap.get("value"));
            }
            // Copy other properties
            if (fileMap.get("filename") != null) {
                multipartMap.put("filename", fileMap.get("filename"));
            }
            if (fileMap.get("contentType") != null) {
                multipartMap.put("contentType", fileMap.get("contentType"));
            }
            if (fileMap.get("charset") != null) {
                multipartMap.put("charset", fileMap.get("charset"));
            }
            if (fileMap.get("transferEncoding") != null) {
                multipartMap.put("transferEncoding", fileMap.get("transferEncoding"));
            }
        } else if (value instanceof String) {
            // Direct string value - could be file path or content
            multipartMap.put("value", value);
        } else if (value instanceof byte[]) {
            multipartMap.put("value", value);
        } else {
            multipartMap.put("value", value);
        }

        http().multiPart(multipartMap);
    }

    /**
     * Handles: multipart field name = 'value'
     */
    private void executeMultipartField(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        if (eqIndex < 0) {
            throw new RuntimeException("multipart field requires '=' assignment: " + text);
        }
        String name = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();

        Object value = runtime.eval(expr);

        Map<String, Object> multipartMap = new HashMap<>();
        multipartMap.put("name", name);
        multipartMap.put("value", value);

        http().multiPart(multipartMap);
    }

    /**
     * Handles: multipart fields { name: 'value', other: 'data' }
     * V1 behavior: if field value is a map with 'value' key, merge it (not nest it)
     */
    @SuppressWarnings("unchecked")
    private void executeMultipartFields(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof Map) {
            Map<String, Object> fields = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                Map<String, Object> multipartMap = new HashMap<>();
                multipartMap.put("name", entry.getKey());
                Object fieldValue = entry.getValue();
                if (fieldValue instanceof Map) {
                    // V1 behavior: merge map fields (e.g., { value: 'x', contentType: 'y' })
                    multipartMap.putAll((Map<String, Object>) fieldValue);
                } else {
                    multipartMap.put("value", fieldValue);
                }
                http().multiPart(multipartMap);
            }
        } else {
            throw new RuntimeException("multipart fields expects a map: " + step.getText());
        }
    }

    /**
     * Handles: multipart files [{ read: 'file1.txt', name: 'file1' }, { read: 'file2.txt', name: 'file2' }]
     * Also handles V1 map syntax: multipart files { myFile1: {...}, myFile2: {...} }
     * where map keys become the part names.
     */
    @SuppressWarnings("unchecked")
    private void executeMultipartFiles(Step step) {
        Object value = evalKarateExpression(step.getText());
        if (value instanceof List) {
            List<Object> files = (List<Object>) value;
            for (Object item : files) {
                processMultipartFileEntry(item, null);
            }
        } else if (value instanceof Map) {
            // V1 compatibility: map where keys are part names
            Map<String, Object> filesMap = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : filesMap.entrySet()) {
                processMultipartFileEntry(entry.getValue(), entry.getKey());
            }
        } else {
            throw new RuntimeException("multipart files expects a list or map: " + step.getText());
        }
    }

    private void processMultipartFileEntry(Object item, String defaultName) {
        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fileMap = (Map<String, Object>) item;
            Map<String, Object> multipartMap = new HashMap<>();

            // Name from map entry key (V1 compat) or from 'name' property
            String name = defaultName != null ? defaultName : (String) fileMap.get("name");
            if (name == null) {
                throw new RuntimeException("multipart files entry requires 'name': " + item);
            }
            multipartMap.put("name", name);

            // Handle file read
            Object readPath = fileMap.get("read");
            if (readPath != null) {
                Resource resource = resolveResource(readPath.toString());
                File file = getFileFromResource(resource);
                if (file != null) {
                    multipartMap.put("value", file);
                } else {
                    try (InputStream is = resource.getStream()) {
                        byte[] bytes = is.readAllBytes();
                        multipartMap.put("value", bytes);
                    } catch (Exception e) {
                        throw new RuntimeException("failed to read file: " + readPath, e);
                    }
                }
            } else if (fileMap.get("value") != null) {
                multipartMap.put("value", fileMap.get("value"));
            }

            // Copy other properties
            if (fileMap.get("filename") != null) {
                multipartMap.put("filename", fileMap.get("filename"));
            }
            if (fileMap.get("contentType") != null) {
                multipartMap.put("contentType", fileMap.get("contentType"));
            }

            http().multiPart(multipartMap);
        } else {
            throw new RuntimeException("multipart files entry must be a map: " + item);
        }
    }

    /**
     * Handles: multipart entity value
     * For sending a single entity as the multipart body (advanced use case)
     */
    @SuppressWarnings("unchecked")
    private void executeMultipartEntity(Step step) {
        String expr = step.getDocString() != null ? step.getDocString() : step.getText();
        Object value = evalKarateExpression(expr);

        if (value instanceof Map) {
            // Single entity map with name, value, etc.
            http().multiPart((Map<String, Object>) value);
        } else {
            // Wrap in a default map
            Map<String, Object> multipartMap = new HashMap<>();
            multipartMap.put("name", "file");
            multipartMap.put("value", value);
            http().multiPart(multipartMap);
        }
    }

    // ========== Multipart Helpers ==========

    private Resource resolveResource(String path) {
        FeatureRuntime fr = runtime.getFeatureRuntime();
        if (fr != null) {
            return fr.resolve(path);
        }
        return Resource.path(path);
    }

    private File getFileFromResource(Resource resource) {
        if (resource.isLocalFile()) {
            Path path = resource.getPath();
            if (path != null && Files.exists(path)) {
                return path.toFile();
            }
        }
        return null;
    }

    // ========== Control Flow ==========

    private void executeCall(Step step) {
        String text = step.getText().trim();

        // Try to evaluate the first token to see if it's a JS function
        // Syntax: "call fun" or "call fun arg" where fun is a JS function variable
        // Note: read('file.js') returns a JS function, read('file.feature') returns a Feature
        int spaceIdx = StepUtils.findCallArgSeparator(text);
        String firstToken = spaceIdx > 0 ? text.substring(0, spaceIdx) : text;

        // Check if it's a read() call for a literal feature file path - use parseCallExpression
        // which properly handles embedded expressions like #(nodes)
        // For read(variable) or read('file.js'), we evaluate to determine the type
        if (StepUtils.isLiteralFeatureRead(text)) {
            // Fall through to standard feature call handling
        } else {
            // Try to evaluate as a JS expression (handles variables, read('file.js'), etc.)
            try {
                Object evaluated = runtime.eval(firstToken);
                if (evaluated instanceof JavaCallable fn) {
                    // It's a JS function - invoke it
                    Object arg = null;
                    if (spaceIdx > 0) {
                        String argExpr = text.substring(spaceIdx + 1).trim();
                        if (!argExpr.isEmpty()) {
                            arg = runtime.eval(argExpr);
                        }
                    }
                    Object result = arg != null
                            ? fn.call(null, arg)
                            : fn.call(null);
                    // V1 behavior: if result is a Map, spread all keys as shared scope variables
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) result;
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            runtime.setVariable(entry.getKey(), entry.getValue());
                        }
                    }
                    return;
                } else if (evaluated instanceof FeatureCall || evaluated instanceof Feature) {
                    // It's a feature or feature call - handle below
                    executeFeatureCall(evaluated, spaceIdx > 0 ? text.substring(spaceIdx + 1).trim() : null, null);
                    return;
                }
            } catch (Exception e) {
                // Not a valid JS expression, fall through to feature call
            }
        }

        // Standard feature call
        CallExpression call = parseCallExpression(text);

        // Resolve the feature file relative to current feature
        FeatureRuntime fr = runtime.getFeatureRuntime();
        Feature calledFeature;

        if (call.sameFile) {
            // Same-file tag call - use current feature
            calledFeature = fr != null ? fr.getFeature() : null;
            if (calledFeature == null) {
                throw new RuntimeException("call with tag selector requires a feature context");
            }
        } else {
            Resource calledResource = fr != null
                    ? fr.resolve(call.path)
                    : Resource.path(call.path);
            calledFeature = Feature.read(calledResource);
        }

        // Check if it's an array loop call
        if (call.argList != null) {
            List<Map<String, Object>> results = new ArrayList<>();
            int loopIndex = 0;

            for (Object item : call.argList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> callArg = item instanceof Map ? (Map<String, Object>) item : null;

                // Create nested FeatureRuntime for each iteration
                FeatureRuntime nestedFr = new FeatureRuntime(
                        fr != null ? fr.getSuite() : null,
                        calledFeature,
                        fr,
                        runtime,
                        false,  // Always isolated scope for array loop
                        callArg,
                        call.tagSelector
                );
                nestedFr.setLoopIndex(loopIndex);

                FeatureResult featureResult = nestedFr.call();

                // Capture feature result for HTML report display (V1 style)
                addCallResult(featureResult);

                if (nestedFr.getLastExecuted() != null) {
                    results.add(nestedFr.getLastExecuted().getAllVariables());
                }
                loopIndex++;
            }

            if (call.resultVar != null) {
                runtime.setVariable(call.resultVar, results);
            }
            return;
        }

        // Determine if shared scope (no resultVar) or isolated scope (has resultVar)
        boolean sharedScope = call.resultVar == null;

        // Create nested FeatureRuntime with optional tag selector
        FeatureRuntime nestedFr = new FeatureRuntime(
                fr != null ? fr.getSuite() : null,
                calledFeature,
                fr,
                runtime,
                sharedScope,
                call.arg,
                call.tagSelector
        );

        // Execute the called feature
        FeatureResult result = nestedFr.call();

        // Capture feature result for HTML report display (V1 style)
        addCallResult(result);

        // Propagate results back to caller
        propagateFromCallee(nestedFr.getLastExecuted(), call.resultVar);
    }

    /**
     * Execute a feature call with a Feature or FeatureCall object.
     * Used when the call expression evaluates to a Feature variable.
     * Supports array loop calling when argExpr evaluates to a List.
     */
    @SuppressWarnings("unchecked")
    private void executeFeatureCall(Object featureObj, String argExpr, String resultVar) {
        FeatureRuntime fr = runtime.getFeatureRuntime();

        Feature calledFeature;
        String tagSelector = null;

        if (featureObj instanceof FeatureCall) {
            FeatureCall fc = (FeatureCall) featureObj;
            if (fc.isSameFile()) {
                calledFeature = fr != null ? fr.getFeature() : null;
                if (calledFeature == null) {
                    throw new RuntimeException("same-file tag call requires a feature context");
                }
            } else {
                calledFeature = fc.getFeature();
            }
            tagSelector = fc.getTagSelector();
        } else if (featureObj instanceof Feature) {
            calledFeature = (Feature) featureObj;
        } else {
            throw new RuntimeException("expected Feature or FeatureCall, got: " + featureObj.getClass());
        }

        // Parse argument expression if provided
        Object argObj = null;
        if (argExpr != null && !argExpr.isEmpty()) {
            argObj = runtime.eval(argExpr);
        }

        // Check if it's an array loop call
        if (argObj instanceof List) {
            List<?> argList = (List<?>) argObj;
            List<Map<String, Object>> results = new ArrayList<>();
            int loopIndex = 0;

            for (Object item : argList) {
                Map<String, Object> callArg = null;
                if (item instanceof Map) {
                    callArg = (Map<String, Object>) item;
                }

                // Create nested FeatureRuntime for each iteration
                FeatureRuntime nestedFr = new FeatureRuntime(
                        fr != null ? fr.getSuite() : null,
                        calledFeature,
                        fr,
                        runtime,
                        false,  // Always isolated scope for array loop
                        callArg,
                        tagSelector
                );
                nestedFr.setLoopIndex(loopIndex);

                FeatureResult featureResult = nestedFr.call();

                // Capture feature result for HTML report display (V1 style)
                addCallResult(featureResult);

                if (nestedFr.getLastExecuted() != null) {
                    results.add(nestedFr.getLastExecuted().getAllVariables());
                }
                loopIndex++;
            }

            if (resultVar != null) {
                runtime.setVariable(resultVar, results);
            }
            return;
        }

        // Single call with Map argument
        Map<String, Object> callArg = null;
        if (argObj instanceof Map) {
            callArg = (Map<String, Object>) argObj;
        }

        // Determine scope
        boolean sharedScope = resultVar == null;

        // Create nested FeatureRuntime
        FeatureRuntime nestedFr = new FeatureRuntime(
                fr != null ? fr.getSuite() : null,
                calledFeature,
                fr,
                runtime,
                sharedScope,
                callArg,
                tagSelector
        );

        // Execute the called feature
        FeatureResult featureResult = nestedFr.call();

        // Capture feature result for HTML report display (V1 style)
        addCallResult(featureResult);

        // Propagate results back to caller
        propagateFromCallee(nestedFr.getLastExecuted(), resultVar);
    }

    private void executeCallOnce(Step step) {
        String text = step.getText().trim();
        String cacheKey = text;

        // Use feature-level cache (not suite-level) - callOnce is scoped per feature
        FeatureRuntime fr = runtime.getFeatureRuntime();
        if (fr == null) {
            // No feature context - just execute normally
            executeCall(step);
            return;
        }

        Map<String, Object> cache = fr.CALLONCE_CACHE;
        java.util.concurrent.locks.ReentrantLock lock = fr.getCallOnceLock();

        // Fast path - check cache without lock
        CallOnceResult cached = (CallOnceResult) cache.get(cacheKey);
        if (cached != null) {
            applyCachedCallOnceResult(cached);
            return;
        }

        // Slow path - acquire lock for execution
        lock.lock();
        try {
            // Double-check after acquiring lock (another thread may have populated cache)
            CallOnceResult rechecked = (CallOnceResult) cache.get(cacheKey);
            if (rechecked != null) {
                applyCachedCallOnceResult(rechecked);
                return;
            }

            // Not cached - execute the call
            executeCall(step);

            // Cache variables, config, and cookie jar (executeCall already copied vars to runtime)
            @SuppressWarnings("unchecked")
            Map<String, Object> vars = (Map<String, Object>) StepUtils.deepCopy(runtime.getAllVariables());
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> cookieJarCopy = (Map<String, Map<String, Object>>) StepUtils.deepCopy(runtime.getCookieJar());
            cache.put(cacheKey, new CallOnceResult(vars, runtime.getConfig().copy(), cookieJarCopy));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cached result from a callonce execution, containing variables, config, and cookie jar.
     */
    private record CallOnceResult(Map<String, Object> vars, KarateConfig config, Map<String, Map<String, Object>> cookieJar) {}

    @SuppressWarnings("unchecked")
    private void applyCachedCallOnceResult(CallOnceResult cached) {
        Map<String, Object> copies = (Map<String, Object>) StepUtils.deepCopy(cached.vars());
        for (Map.Entry<String, Object> entry : copies.entrySet()) {
            runtime.setVariable(entry.getKey(), entry.getValue());
        }
        runtime.getConfig().copyFrom(cached.config());
        // V1 compatibility: Also restore cookie jar from cache
        if (cached.cookieJar() != null) {
            Map<String, Map<String, Object>> cookieJarCopy = (Map<String, Map<String, Object>>) StepUtils.deepCopy(cached.cookieJar());
            runtime.getCookieJar().putAll(cookieJarCopy);
        }
    }

    /**
     * Parses call expression like:
     * - read('file.feature')
     * - read('file.feature') { arg: 1 }
     * - read('file.feature') argVar
     */
    private CallExpression parseCallExpression(String text) {
        CallExpression expr = new CallExpression();

        // Check if it's a read() call
        if (text.startsWith("read(")) {
            int closeParen = text.indexOf(')');
            if (closeParen > 0) {
                // Extract path from read('path')
                String readArg = text.substring(5, closeParen).trim();
                // Remove quotes
                String rawPath;
                if ((readArg.startsWith("'") && readArg.endsWith("'")) ||
                        (readArg.startsWith("\"") && readArg.endsWith("\""))) {
                    rawPath = readArg.substring(1, readArg.length() - 1);
                } else {
                    // It's a variable reference
                    Object pathObj = runtime.eval(readArg);
                    rawPath = pathObj != null ? pathObj.toString() : readArg;
                }

                // Parse tag selector from path
                parsePathAndTag(rawPath, expr);

                // Check for arguments after the read()
                String remainder = text.substring(closeParen + 1).trim();
                if (!remainder.isEmpty()) {
                    // Evaluate as JS - could be an object literal, array, or variable
                    Object argObj = runtime.eval(remainder);
                    if (argObj instanceof List) {
                        // Array argument - for loop calls
                        expr.argList = (List<?>) argObj;
                    } else if (argObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> argMap = (Map<String, Object>) argObj;
                        // Process embedded expressions in call arguments (e.g., { nodes: '#(nodes)' })
                        Object processed = processEmbeddedExpressions(argMap);
                        if (processed instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> processedMap = (Map<String, Object>) processed;
                            expr.arg = processedMap;
                        }
                    }
                }
            }
        } else {
            // Not a read() call - try evaluating as expression
            // Could be a variable that holds a feature path
            Object result = runtime.eval(text);
            if (result instanceof String) {
                parsePathAndTag((String) result, expr);
            } else {
                throw new RuntimeException("call expression must resolve to a feature path: " + text);
            }
        }

        return expr;
    }

    /**
     * Parse path and tag selector from a feature path.
     * Supports:
     * - file.feature@tag - call specific scenario by tag
     * - @tag - call scenario in same file by tag
     */
    private void parsePathAndTag(String rawPath, CallExpression expr) {
        StepUtils.ParsedFeaturePath parsed = StepUtils.parseFeaturePath(rawPath);
        expr.path = parsed.path();
        expr.tagSelector = parsed.tagSelector();
        expr.sameFile = parsed.sameFile();
    }

    private static class CallExpression {
        String path;
        Map<String, Object> arg;
        List<?> argList;     // For loop calls with array argument
        String resultVar;
        String tagSelector;  // For call-by-tag syntax
        boolean sameFile;    // true if calling scenario in same file
    }

    private void executeEval(Step step) {
        String expr = step.getDocString();
        if (expr != null) {
            runtime.evalDocString(expr, step);
        } else {
            expr = step.getText();
            runtime.eval(expr, step);
        }
    }

    @SuppressWarnings("unchecked")
    private void executeDoc(Step step) {
        String text = step.getText();
        Object value = runtime.eval(text);
        // karate.doc() expects either a string path or a map with 'read' key
        // Convert string to the expected format
        String html;
        if (value instanceof String path) {
            html = runtime.getKarate().doc(Map.of("read", path));
        } else if (value instanceof Map) {
            html = runtime.getKarate().doc((Map<String, Object>) value);
        } else {
            throw new RuntimeException("doc requires a string path or map with 'read' key, got: " + value);
        }
        // Embed the rendered HTML in the step result for reports
        if (html != null && !html.isEmpty()) {
            LogContext.get().embed(html.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html");
        }
    }

    // ========== Config ==========

    private void executeConfigure(Step step) {
        String text = step.getText();
        int eqIndex = StepUtils.findAssignmentOperator(text);
        if (eqIndex < 0) {
            throw new RuntimeException("configure requires '=' assignment: " + text);
        }
        String key = text.substring(0, eqIndex).trim();
        String expr = text.substring(eqIndex + 1).trim();
        Object value = evalKarateExpression(expr);
        runtime.configure(key, value);
    }

    // ========== Browser Driver ==========

    /**
     * Execute the driver keyword - navigate to a URL.
     * Examples:
     * - driver 'http://localhost:8080'
     * - driver serverUrl + '/path'
     * - driver myUrl
     */
    private void executeDriver(Step step) {
        String text = step.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new RuntimeException("driver keyword requires a URL argument");
        }

        // Evaluate the expression to get the URL
        Object result = runtime.eval(text.trim());
        if (result == null) {
            throw new RuntimeException("driver URL evaluated to null: " + text);
        }

        String url = result.toString();

        // Get the driver (initializes lazily if needed)
        io.karatelabs.driver.Driver driver = runtime.getDriver();

        // Navigate to the URL
        driver.setUrl(url);
    }

    // ========== Karate Expression Helpers ==========

    /**
     * Gets a response header value by name (case-insensitive).
     * V1 compatibility: "match header Content-Type contains '...'"
     * Returns the first value if header has multiple values, null if not found.
     */
    @SuppressWarnings("unchecked")
    private Object getResponseHeader(String headerName) {
        Object responseHeaders = runtime.getVariable("responseHeaders");
        if (responseHeaders instanceof Map) {
            Map<String, Object> headers = (Map<String, Object>) responseHeaders;
            Object headerValue = StringUtils.getIgnoreKeyCase(headers, headerName);
            if (headerValue instanceof List<?> list) {
                return list.isEmpty() ? null : list.get(0);
            }
            return headerValue;
        }
        return null;
    }

    /**
     * Evaluates a call expression and returns the result.
     * Used for RHS expressions like: header X = call fun { arg: 1 }
     */
    private Object evalCallExpression(String callExpr) {
        String tempVar = "__callResult__" + System.nanoTime();
        executeCallWithResult(callExpr, tempVar);
        return runtime.getVariable(tempVar);
    }

    /**
     * Evaluates a callonce expression and returns the result.
     * Used for RHS expressions like: def x = callonce read('setup.feature')
     */
    private Object evalCallOnceExpression(String callExpr) {
        String tempVar = "__callOnceResult__" + System.nanoTime();
        executeCallOnceWithResult(callExpr, tempVar);
        return runtime.getVariable(tempVar);
    }

    /**
     * Evaluates get[N] varname path or get varname path syntax.
     * Examples: get[0] foo[*].name, get foo $..bar, get xml //xpath
     */
    private Object evalGetExpression(String expr) {
        int index = -1;
        String remainder;

        if (expr.startsWith("get[")) {
            // get[N] syntax
            int closeBracket = expr.indexOf(']');
            if (closeBracket < 0) {
                throw new RuntimeException("Invalid get expression, missing ]: " + expr);
            }
            index = Integer.parseInt(expr.substring(4, closeBracket));
            remainder = expr.substring(closeBracket + 1).trim();
        } else {
            // get varname path
            remainder = expr.substring(4).trim();
        }

        // Parse varname and path - could be "varname path" or "varname[...]"
        String varName;
        String path;

        // Check if path starts with $ (explicit jsonpath)
        if (remainder.startsWith("$")) {
            // get $..path or get[0] $[*].foo - operates on 'response'
            varName = "response";
            path = remainder;
        } else {
            // Find space or bracket to split varname from path
            int spaceIdx = remainder.indexOf(' ');
            int bracketIdx = remainder.indexOf('[');

            if (spaceIdx > 0 && (bracketIdx < 0 || spaceIdx < bracketIdx)) {
                // "varname path" format - e.g., "json $['sp ace']" or "json .foo" or "xml //xpath"
                varName = remainder.substring(0, spaceIdx);
                path = remainder.substring(spaceIdx).trim();
            } else if (bracketIdx > 0) {
                // "varname[*].path" format
                varName = remainder.substring(0, bracketIdx);
                path = remainder.substring(bracketIdx);
            } else {
                // Just varname, no path
                varName = remainder;
                path = null;
            }
        }

        Object target = runtime.getVariable(varName);
        if (target == null) {
            return null;
        }

        // Handle XPath for XML nodes
        if (target instanceof Node && path != null && path.startsWith("/")) {
            Object result = KarateJsUtils.evalXmlPath((Node) target, path);
            // Apply index if specified
            if (index >= 0 && result instanceof List) {
                List<?> list = (List<?>) result;
                if (index < list.size()) {
                    return list.get(index);
                }
                return null;
            }
            return result;
        }

        // Default to JsonPath
        String jsonPath;
        if (path == null) {
            jsonPath = "$";
        } else if (path.startsWith("$")) {
            jsonPath = path;
        } else {
            jsonPath = "$" + path;
        }

        Object result = JsonPath.read(target, jsonPath);

        // Apply index if specified
        if (index >= 0 && result instanceof List) {
            List<?> list = (List<?>) result;
            if (index < list.size()) {
                return list.get(index);
            }
            return null;
        }

        return result;
    }

    // ========== Embedded Expression Processing ==========

    // evalKarateExpression removed - use evalKarateExpression which handles all Karate patterns

    /**
     * Marker object to indicate a key should be removed (for ##() optional expressions).
     */
    private static final Object REMOVE_MARKER = new Object();

    /**
     * Process embedded expressions (#() and ##()) in a value.
     * - #(expr) evaluates expr and substitutes the result
     * - ##(expr) evaluates expr; if null, removes the key (returns REMOVE_MARKER)
     */
    private Object processEmbeddedExpressions(Object value) {
        if (value instanceof Node) {
            processXmlEmbeddedExpressions((Node) value);
            return value;
        } else if (value instanceof JavaCallable) {
            // JsCallable functions shouldn't be processed as maps - return them unchanged
            return value;
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object processed = processEmbeddedExpressions(entry.getValue());
                if (processed != REMOVE_MARKER) {
                    result.put(entry.getKey(), processed);
                }
            }
            return result;
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                Object processed = processEmbeddedExpressions(item);
                if (processed != REMOVE_MARKER) {
                    result.add(processed);
                }
            }
            return result;
        } else if (value instanceof String str) {
            return processEmbeddedString(str);
        }
        return value;
    }

    /**
     * Process a string that may contain embedded expressions.
     */
    private Object processEmbeddedString(String str) {
        // Check for optional embedded: ##(...)
        if (str.startsWith("##(") && str.endsWith(")")) {
            String expr = str.substring(3, str.length() - 1);
            try {
                Object result = runtime.eval(expr);
                return result == null ? REMOVE_MARKER : result;
            } catch (Exception e) {
                // For optional ##() expressions, treat undefined variables as null (remove)
                // V1 compatibility: undefined variables in ##() should result in removal
                if (e.getMessage() != null && e.getMessage().contains("is not defined")) {
                    return REMOVE_MARKER;
                }
                throw e;
            }
        }
        // Check for regular embedded: #(...)
        if (str.startsWith("#(") && str.endsWith(")")) {
            String expr = str.substring(2, str.length() - 1);
            return runtime.eval(expr);
        }
        // Check for embedded expressions within a larger string
        // e.g., "Hello #(name)!" or "Value: ##(optional)"
        if (str.contains("#(")) {
            return processInlineEmbedded(str);
        }
        return str;
    }

    /**
     * Process inline embedded expressions like "Hello #(name)!"
     */
    private Object processInlineEmbedded(String str) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < str.length()) {
            int hashPos = str.indexOf('#', i);
            if (hashPos < 0) {
                result.append(str.substring(i));
                break;
            }
            result.append(str.substring(i, hashPos));

            boolean optional = false;
            int exprStart;
            if (hashPos + 1 < str.length() && str.charAt(hashPos + 1) == '#') {
                // ##( optional
                if (hashPos + 2 < str.length() && str.charAt(hashPos + 2) == '(') {
                    optional = true;
                    exprStart = hashPos + 3;
                } else {
                    result.append("##");
                    i = hashPos + 2;
                    continue;
                }
            } else if (hashPos + 1 < str.length() && str.charAt(hashPos + 1) == '(') {
                // #( regular
                exprStart = hashPos + 2;
            } else {
                result.append('#');
                i = hashPos + 1;
                continue;
            }

            // Find matching closing paren
            int depth = 1;
            int j = exprStart;
            while (j < str.length() && depth > 0) {
                char c = str.charAt(j);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                j++;
            }
            if (depth != 0) {
                // Unbalanced parens, treat as literal
                result.append(optional ? "##(" : "#(");
                i = exprStart;
                continue;
            }

            String expr = str.substring(exprStart, j - 1);
            Object value = runtime.eval(expr);
            if (optional && value == null) {
                // For inline optional, substitute empty string
                // (key removal only applies to whole-value expressions)
            } else if (value != null) {
                result.append(StepUtils.stringify(value));
            }
            i = j;
        }
        return result.toString();
    }

    /**
     * Process embedded expressions in XML nodes.
     * Handles both attribute values and text content with #() and ##() expressions.
     */
    private void processXmlEmbeddedExpressions(Node node) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        if (node == null) return;

        // Process attributes
        org.w3c.dom.NamedNodeMap attribs = node.getAttributes();
        if (attribs != null) {
            List<org.w3c.dom.Attr> toRemove = new ArrayList<>();
            for (int i = 0; i < attribs.getLength(); i++) {
                org.w3c.dom.Attr attrib = (org.w3c.dom.Attr) attribs.item(i);
                String value = attrib.getValue();
                if (value != null && value.contains("#(")) {
                    boolean optional = value.startsWith("##(");
                    if (value.startsWith("#(") || optional) {
                        String expr = value.substring(optional ? 3 : 2, value.length() - 1);
                        try {
                            Object result = runtime.eval(expr);
                            if (optional && result == null) {
                                toRemove.add(attrib);
                            } else {
                                attrib.setValue(result == null ? "" : StepUtils.stringify(result));
                            }
                        } catch (Exception e) {
                            // Leave as-is on error
                        }
                    } else {
                        // Inline embedded in attribute
                        attrib.setValue(processInlineEmbedded(value).toString());
                    }
                }
            }
            for (org.w3c.dom.Attr attr : toRemove) {
                attribs.removeNamedItem(attr.getName());
            }
        }

        // Process child nodes
        org.w3c.dom.NodeList children = node.getChildNodes();
        List<Node> childList = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            childList.add(children.item(i));
        }

        List<Node> elementsToRemove = new ArrayList<>();
        for (Node child : childList) {
            String value = child.getNodeValue();
            if (value != null) {
                value = value.trim();
                if (value.startsWith("#(") || value.startsWith("##(")) {
                    boolean optional = value.startsWith("##(");
                    String expr = value.substring(optional ? 3 : 2, value.length() - 1);
                    try {
                        Object result = runtime.eval(expr);
                        if (optional && result == null) {
                            elementsToRemove.add(child);
                        } else if (result instanceof Node evalNode && child.getNodeType() != Node.CDATA_SECTION_NODE) {
                            // Replace text node with XML node (but not for CDATA)
                            if (evalNode.getNodeType() == Node.DOCUMENT_NODE) {
                                evalNode = evalNode.getFirstChild();
                            }
                            evalNode = node.getOwnerDocument().importNode(evalNode, true);
                            child.getParentNode().replaceChild(evalNode, child);
                        } else {
                            // For CDATA or non-Node results, convert to string
                            String strResult = (result instanceof Node)
                                    ? Xml.toString((Node) result, false)
                                    : (result == null ? "" : StepUtils.stringify(result));
                            child.setNodeValue(strResult);
                        }
                    } catch (Exception e) {
                        // Leave as-is on error
                    }
                } else if (value.contains("#(")) {
                    // Inline embedded in text
                    child.setNodeValue(processInlineEmbedded(value).toString());
                }
            } else if (child.hasChildNodes() || child.hasAttributes()) {
                processXmlEmbeddedExpressions(child);
            }
        }

        // Remove elements marked for removal (for ##() that evaluated to null)
        for (Node toRemove : elementsToRemove) {
            Node parent = toRemove.getParentNode();
            if (parent != null) {
                Node grandParent = parent.getParentNode();
                if (grandParent != null) {
                    grandParent.removeChild(parent);
                }
            }
        }
    }

}
