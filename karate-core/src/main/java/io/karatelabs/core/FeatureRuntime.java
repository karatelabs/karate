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
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.FeatureSection;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.ScenarioOutline;
import io.karatelabs.gherkin.ExamplesTable;
import io.karatelabs.gherkin.Table;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.output.LogContext;
import io.karatelabs.output.ResultListener;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class FeatureRuntime implements Callable<FeatureResult> {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    private final Suite suite;
    private final Feature feature;
    private final FeatureRuntime caller;
    private final ScenarioRuntime callerScenario;  // The calling scenario's runtime (for variable inheritance)
    private final boolean sharedScope;  // true = pass variables by reference, false = pass copies
    private final Map<String, Object> callArg;
    private final String callTagSelector;  // Tag selector for call-by-tag (e.g., "@name=second")

    // Caches (feature-level)
    final Map<String, Object> CALLONCE_CACHE = new ConcurrentHashMap<>();
    final Map<String, Object> SETUPONCE_CACHE = new ConcurrentHashMap<>();
    private final ReentrantLock callOnceLock = new ReentrantLock();

    // State
    private ScenarioRuntime lastExecuted;
    private FeatureResult result;
    private final Map<Integer, Integer> outlineCompletedCounts = new HashMap<>();  // section index -> completed count
    private int loopIndex = -1;  // -1 means not a loop call; 0+ is the iteration index

    public FeatureRuntime(Feature feature) {
        this(null, feature, null, null, false, null);
    }

    public FeatureRuntime(Suite suite, Feature feature) {
        this(suite, feature, null, null, false, null);
    }

    public FeatureRuntime(Suite suite, Feature feature, FeatureRuntime caller, Map<String, Object> callArg) {
        this(suite, feature, caller, null, false, callArg);
    }

    public FeatureRuntime(Suite suite, Feature feature, FeatureRuntime caller, ScenarioRuntime callerScenario, boolean sharedScope, Map<String, Object> callArg) {
        this(suite, feature, caller, callerScenario, sharedScope, callArg, null);
    }

    public FeatureRuntime(Suite suite, Feature feature, FeatureRuntime caller, ScenarioRuntime callerScenario, boolean sharedScope, Map<String, Object> callArg, String callTagSelector) {
        this.suite = suite;
        this.feature = feature;
        this.caller = caller;
        this.callerScenario = callerScenario;
        this.sharedScope = sharedScope;
        this.callArg = callArg;
        this.callTagSelector = callTagSelector;
        this.result = new FeatureResult(feature);
    }

    public static FeatureRuntime of(Feature feature) {
        return new FeatureRuntime(feature);
    }

    public static FeatureRuntime of(Suite suite, Feature feature) {
        return new FeatureRuntime(suite, feature);
    }

    @Override
    public FeatureResult call() {
        result.setStartTime(System.currentTimeMillis());

        // Notify listeners of feature start
        if (suite != null) {
            for (ResultListener listener : suite.getResultListeners()) {
                listener.onFeatureStart(feature);
            }
        }

        try {
            // Fire FEATURE_ENTER event
            if (suite != null) {
                suite.fireEvent(FeatureRunEvent.enter(this));
            }

            try {
                // Use scenario-level parallelism for top-level features in parallel mode
                // Must check BOTH executor AND semaphore to ensure proper synchronization
                if (suite != null && suite.parallel && caller == null
                        && suite.getScenarioExecutor() != null && suite.getScenarioSemaphore() != null) {
                    logger.debug("Running scenarios in parallel mode for feature: {}", feature.getName());
                    runScenariosParallel();
                } else {
                    // Sequential execution for called features or non-parallel mode
                    if (suite != null && suite.parallel && caller == null) {
                        logger.warn("Falling back to sequential execution for feature '{}' - executor: {}, semaphore: {}",
                                feature.getName(), suite.getScenarioExecutor() != null, suite.getScenarioSemaphore() != null);
                    }
                    for (Scenario scenario : selectedScenarios()) {
                        executeScenario(scenario);
                    }
                }
            } catch (Exception e) {
                // Handle errors during scenario iteration (e.g., dynamic expression evaluation failure)
                // Create a synthetic failed scenario result so the feature fails gracefully
                logger.error("Error during scenario iteration: {}", e.getMessage());
                ScenarioResult errorResult = createErrorScenarioResult(e);
                result.addScenarioResult(errorResult);
            }

            // Invoke configured afterFeature hook if present (only for top-level features)
            if (lastExecuted != null && caller == null) {
                invokeAfterFeatureHook(lastExecuted);
            }

            // Fire FEATURE_EXIT event
            if (suite != null) {
                suite.fireEvent(FeatureRunEvent.exit(this, result));
            }
        } finally {
            result.setEndTime(System.currentTimeMillis());

            // Notify listeners of feature end (only for top-level features, not nested calls)
            if (suite != null && caller == null) {
                for (ResultListener listener : suite.getResultListeners()) {
                    listener.onFeatureEnd(result);
                }
            }
        }

        return result;
    }

    /**
     * Run scenarios in parallel using the Suite's shared executor.
     * Each scenario is dispatched to the executor, limited by the scenario semaphore.
     * The iterator produces scenarios lazily (supporting dynamic @setup scenarios).
     */
    private void runScenariosParallel() {
        ExecutorService executor = suite.getScenarioExecutor();
        Semaphore semaphore = suite.getScenarioSemaphore();
        List<Future<ScenarioResult>> futures = new ArrayList<>();

        // Dispatch scenarios to executor as they are produced by the iterator
        // Dynamic scenarios (from @setup) are evaluated lazily during iteration
        for (Scenario scenario : selectedScenarios()) {
            Future<ScenarioResult> future = executor.submit(() -> {
                String scenarioName = scenario.getName();
                // Acquire semaphore to limit concurrent scenarios
                logger.debug("Waiting for semaphore permit: {} (available: {})", scenarioName, semaphore.availablePermits());
                semaphore.acquire();
                logger.debug("Acquired semaphore permit: {} (available: {})", scenarioName, semaphore.availablePermits());
                // Acquire a lane for timeline reporting (consistent lane names instead of random thread IDs)
                suite.acquireLane();
                try {
                    return executeScenarioParallel(scenario);
                } finally {
                    suite.releaseLane();
                    semaphore.release();
                    logger.debug("Released semaphore permit: {} (available: {})", scenarioName, semaphore.availablePermits());
                }
            });
            futures.add(future);
        }

        // Wait for all scenarios and collect results
        for (Future<ScenarioResult> future : futures) {
            try {
                ScenarioResult scenarioResult = future.get();
                result.addScenarioResult(scenarioResult);
            } catch (Exception e) {
                logger.error("Error collecting scenario result: {}", e.getMessage());
                // Create a synthetic error result
                ScenarioResult errorResult = createErrorScenarioResult(e);
                result.addScenarioResult(errorResult);
            }
        }
    }

    /**
     * Execute a scenario in parallel mode.
     * Handles locking, execution, and result collection.
     * Returns the ScenarioResult for aggregation by the caller.
     */
    private ScenarioResult executeScenarioParallel(Scenario scenario) {
        // Notify listeners of scenario start
        for (ResultListener listener : suite.getResultListeners()) {
            listener.onScenarioStart(scenario);
        }

        // Acquire locks for @lock tags
        ScenarioLockManager.LockHandle lockHandle = suite.getLockManager().acquire(scenario);

        ScenarioResult scenarioResult;
        try {
            ScenarioRuntime sr = new ScenarioRuntime(this, scenario);
            scenarioResult = sr.call();
            // Note: lastExecuted tracking not meaningful in parallel mode
            // afterScenarioOutline hook is also not applicable in parallel mode

        } catch (Exception e) {
            logger.error("Error executing scenario '{}': {}", scenario.getName(), e.getMessage());
            scenarioResult = createErrorScenarioResult(scenario, e);
        } finally {
            // Release locks
            if (lockHandle != null) {
                suite.getLockManager().release(lockHandle);
            }
        }

        // Notify listeners of scenario completion
        for (ResultListener listener : suite.getResultListeners()) {
            listener.onScenarioEnd(scenarioResult);
        }

        return scenarioResult;
    }

    /**
     * Execute a single scenario with exception handling (sequential mode).
     * If the scenario throws an exception, it's captured as a failed scenario result
     * rather than propagating up and crashing the feature.
     */
    private void executeScenario(Scenario scenario) {
        // Notify listeners of scenario start (only for top-level features)
        if (suite != null && caller == null) {
            for (ResultListener listener : suite.getResultListeners()) {
                listener.onScenarioStart(scenario);
            }
        }

        // Acquire locks for @lock tags (only for top-level features)
        ScenarioLockManager.LockHandle lockHandle = null;
        if (suite != null && caller == null) {
            lockHandle = suite.getLockManager().acquire(scenario);
        }

        ScenarioResult scenarioResult;
        try {
            ScenarioRuntime sr = new ScenarioRuntime(this, scenario);
            scenarioResult = sr.call();
            lastExecuted = sr;

            // Check if this is the last scenario in an outline (only for top-level features)
            if (caller == null && isLastScenarioInOutline(scenario)) {
                invokeAfterScenarioOutlineHook(sr);
            }
        } catch (Exception e) {
            // Handle unexpected errors during scenario execution
            logger.error("Error executing scenario '{}': {}", scenario.getName(), e.getMessage());
            scenarioResult = createErrorScenarioResult(scenario, e);
        } finally {
            // Release locks (only for top-level features)
            if (suite != null && caller == null && lockHandle != null) {
                suite.getLockManager().release(lockHandle);
            }
        }

        result.addScenarioResult(scenarioResult);

        // Notify listeners of scenario completion (only for top-level features)
        if (suite != null && caller == null) {
            for (ResultListener listener : suite.getResultListeners()) {
                listener.onScenarioEnd(scenarioResult);
            }
        }
    }

    /**
     * Create a failed ScenarioResult from an exception when no scenario is available.
     * Used for errors during scenario iteration (e.g., dynamic expression evaluation).
     */
    private ScenarioResult createErrorScenarioResult(Exception error) {
        // Create a minimal scenario for error reporting
        Scenario errorScenario = feature.getSections().isEmpty()
                ? Scenario.createError(feature, "Feature execution failed", feature.getLine())
                : feature.getSections().get(0).isOutline()
                ? feature.getSections().get(0).getScenarioOutline().toScenario(null, 0, feature.getLine(), null)
                : feature.getSections().get(0).getScenario();

        return createErrorScenarioResult(errorScenario, error);
    }

    /**
     * Create a failed ScenarioResult from an exception for a specific scenario.
     */
    private ScenarioResult createErrorScenarioResult(Scenario scenario, Exception error) {
        long now = System.currentTimeMillis();
        ScenarioResult scenarioResult = new ScenarioResult(scenario);
        scenarioResult.setStartTime(now);
        scenarioResult.setEndTime(now);
        scenarioResult.setThreadName(Thread.currentThread().getName());

        String errorMessage = "Scenario execution failed: " + error.getMessage();
        scenarioResult.addStepResult(StepResult.fakeFailure(errorMessage, now, error));

        return scenarioResult;
    }

    /**
     * Invokes the configured afterFeature hook if present.
     * Uses the last executed scenario's runtime context.
     */
    private void invokeAfterFeatureHook(ScenarioRuntime sr) {
        KarateConfig config = sr.getConfig();
        Object afterFeature = config.getAfterFeature();
        if (afterFeature instanceof JavaCallable callable) {
            try {
                callable.call(null);
            } catch (Exception e) {
                logger.warn("afterFeature hook failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Checks if the given scenario is the last one in its scenario outline.
     * Tracks completed counts per outline section.
     */
    private boolean isLastScenarioInOutline(Scenario scenario) {
        if (!scenario.isOutlineExample()) {
            return false;
        }
        FeatureSection section = scenario.getSection();
        int sectionIndex = section.getIndex();
        ScenarioOutline outline = section.getScenarioOutline();

        // Increment completed count for this outline
        int completed = outlineCompletedCounts.merge(sectionIndex, 1, Integer::sum);

        // Check if all scenarios in this outline are completed
        return completed == outline.getNumScenarios();
    }

    /**
     * Invokes the configured afterScenarioOutline hook if present.
     */
    private void invokeAfterScenarioOutlineHook(ScenarioRuntime sr) {
        KarateConfig config = sr.getConfig();
        Object afterScenarioOutline = config.getAfterScenarioOutline();
        if (afterScenarioOutline instanceof JavaCallable callable) {
            try {
                callable.call(null);
            } catch (Exception e) {
                logger.warn("afterScenarioOutline hook failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns an iterable of scenarios to execute, including expanded outlines.
     * Applies tag filtering if configured.
     */
    private Iterable<Scenario> selectedScenarios() {
        return () -> new ScenarioIterator();
    }

    /**
     * Iterator that expands scenario outlines into individual scenarios.
     */
    private class ScenarioIterator implements Iterator<Scenario> {

        private final List<FeatureSection> sections;
        private int sectionIndex = 0;
        private int exampleTableIndex = 0;
        private int exampleRowIndex = 0;
        private Scenario nextScenario = null;

        // For dynamic scenarios
        private List<?> dynamicData = null;
        private Scenario dynamicTemplateScenario = null;

        ScenarioIterator() {
            this.sections = feature.getSections();
            advance();
        }

        @Override
        public boolean hasNext() {
            return nextScenario != null;
        }

        @Override
        public Scenario next() {
            Scenario current = nextScenario;
            advance();
            return current;
        }

        private void advance() {
            nextScenario = null;

            // Continue processing dynamic data if available
            if (dynamicData != null && dynamicTemplateScenario != null) {
                if (processDynamicData()) {
                    return;
                }
            }

            while (sectionIndex < sections.size()) {
                FeatureSection section = sections.get(sectionIndex);

                if (section.isOutline()) {
                    ScenarioOutline outline = section.getScenarioOutline();
                    List<ExamplesTable> tables = outline.getExamplesTables();

                    while (exampleTableIndex < tables.size()) {
                        ExamplesTable table = tables.get(exampleTableIndex);

                        // Check if this is a dynamic Examples table
                        if (table.getTable().isDynamic()) {
                            // Create template scenario for dynamic expansion
                            Scenario templateScenario = outline.toScenario(
                                    table.getTable().getDynamicExpression(),
                                    -1,
                                    table.getLine(),
                                    table.getTags()
                            );

                            // Evaluate the dynamic expression
                            List<?> data = evaluateDynamicExpression(templateScenario);
                            if (data != null && !data.isEmpty()) {
                                dynamicData = data;
                                dynamicTemplateScenario = templateScenario;
                                exampleRowIndex = 0;

                                if (processDynamicData()) {
                                    exampleTableIndex++;
                                    return;
                                }
                            }

                            // Move to next examples table
                            exampleTableIndex++;
                            exampleRowIndex = 0;
                            dynamicData = null;
                            dynamicTemplateScenario = null;
                            continue;
                        }

                        int rowCount = table.getTable().getRows().size() - 1; // exclude header row

                        if (exampleRowIndex < rowCount) {
                            // Use getExampleData which handles type hints (columns ending with !)
                            Map<String, Object> exampleData = table.getTable().getExampleData(exampleRowIndex);
                            int exampleIndex = exampleRowIndex;
                            exampleRowIndex++;

                            // Create scenario from outline
                            Scenario scenario = outline.toScenario(
                                    null,
                                    exampleIndex,
                                    table.getLine(),
                                    table.getTags()
                            );
                            scenario.setExampleData(exampleData);

                            // Substitute placeholders in steps
                            for (String key : exampleData.keySet()) {
                                Object value = exampleData.get(key);
                                // Empty cells become null but should be replaced with empty string
                                scenario.replace("<" + key + ">", value != null ? value.toString() : "");
                            }

                            // Check if scenario should be selected
                            if (shouldSelect(scenario)) {
                                nextScenario = scenario;
                                return;
                            }
                            continue;
                        }

                        // Move to next examples table
                        exampleTableIndex++;
                        exampleRowIndex = 0;
                    }

                    // Move to next section
                    sectionIndex++;
                    exampleTableIndex = 0;
                    exampleRowIndex = 0;

                } else {
                    Scenario scenario = section.getScenario();
                    sectionIndex++;

                    // Skip @setup scenarios - they're only run via karate.setup()
                    if (scenario.isSetup()) {
                        continue;
                    }

                    if (shouldSelect(scenario)) {
                        nextScenario = scenario;
                        return;
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private boolean processDynamicData() {
            while (exampleRowIndex < dynamicData.size()) {
                Object item = dynamicData.get(exampleRowIndex);
                int rowIndex = exampleRowIndex;
                exampleRowIndex++;

                if (item instanceof Map) {
                    Map<String, Object> exampleData = (Map<String, Object>) item;

                    // Create a copy of the template scenario for this row
                    Scenario scenario = dynamicTemplateScenario.copy(rowIndex);
                    scenario.setExampleData(exampleData);

                    // Substitute placeholders in steps
                    for (String key : exampleData.keySet()) {
                        Object value = exampleData.get(key);
                        // Empty cells become null but should be replaced with empty string
                        scenario.replace("<" + key + ">", value != null ? value.toString() : "");
                    }

                    if (shouldSelect(scenario)) {
                        nextScenario = scenario;
                        return true;
                    }
                }
                // Skip non-map items
            }

            // Done with this dynamic data
            dynamicData = null;
            dynamicTemplateScenario = null;
            return false;
        }

        @SuppressWarnings("unchecked")
        private List<?> evaluateDynamicExpression(Scenario templateScenario) {
            try {
                // Create a temporary ScenarioRuntime to evaluate the expression
                ScenarioRuntime sr = new ScenarioRuntime(FeatureRuntime.this, templateScenario);
                sr.setSkipBackground(true);

                // Evaluate the dynamic expression in this runtime's engine
                String expression = templateScenario.getDynamicExpression();
                Object result = sr.eval(expression);

                if (result instanceof List) {
                    return (List<?>) result;
                } else if (result instanceof JavaCallable) {
                    // Generator function - call repeatedly until null/non-map
                    return evaluateGeneratorFunction(result);
                } else {
                    // Expression didn't return a list or function - error
                    throw new RuntimeException("Dynamic expression must return a list or function: " + expression + ", got: " + (result != null ? result.getClass().getName() : "null"));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to evaluate dynamic expression: " + templateScenario.getDynamicExpression(), e);
            }
        }

        /**
         * Evaluates a generator function by calling it repeatedly with incrementing index
         * until it returns null or a non-Map value.
         */
        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> evaluateGeneratorFunction(Object function) {
            List<Map<String, Object>> results = new ArrayList<>();
            JavaCallable callable = (JavaCallable) function;
            int index = 0;

            while (true) {
                Object rowValue;
                try {
                    // JsCallable.call() works with null context (uses declared context internally)
                    rowValue = callable.call(null, index);
                } catch (Exception e) {
                    logger.warn("Generator function threw exception at index {}: {}", index, e.getMessage());
                    break;
                }

                if (rowValue == null) {
                    // null signals end of iteration
                    break;
                }

                if (rowValue instanceof Map) {
                    results.add((Map<String, Object>) rowValue);
                } else {
                    // Non-map value signals end of iteration
                    logger.debug("Generator function returned non-map at index {}, stopping: {}", index, rowValue);
                    break;
                }

                index++;
            }

            return results;
        }

        private boolean shouldSelect(Scenario scenario) {
            // Check line filter first (if specified)
            // Line filter takes precedence for scenario selection
            if (suite != null && !suite.lineFilters.isEmpty()) {
                String featureUri = feature.getResource().getUri().toString();
                Set<Integer> lines = suite.lineFilters.get(featureUri);
                if (lines != null && !lines.isEmpty()) {
                    // Line filter is specified for this feature
                    if (!matchesLineFilter(scenario, lines)) {
                        return false;
                    }
                    // Line filter matched - skip other filters (@ignore, @env, tags)
                    // This allows running specific scenarios regardless of tags
                    return true;
                }
            }

            // Apply call-level tag filter if specified (takes precedence)
            // This allows calling specific @ignore scenarios by tag
            if (callTagSelector != null) {
                return matchesCallTag(scenario, callTagSelector);
            }

            // For called features (caller != null), don't filter by @ignore
            // @ignore only excludes scenarios from top-level runner selection
            if (caller != null) {
                return true;
            }

            // Skip tag filtering if configured (run all scenarios regardless of @env, @ignore)
            if (suite != null && suite.isSkipTagFiltering()) {
                return true;
            }

            // Use TagSelector for suite-level filtering
            // This handles @ignore, @setup, @env, and complex expressions like anyOf(), allOf()
            List<Tag> tags = scenario.getTagsEffective();
            TagSelector selector = new TagSelector(tags);
            String karateEnv = suite != null ? suite.env : null;
            return selector.evaluate(suite != null ? suite.tagSelector : null, karateEnv);
        }

        /**
         * Check if a scenario matches the line filter.
         * For regular scenarios: line must match the scenario's line or be within its range.
         * For outline examples: line must match the example row's line, the outline's line,
         * or any line within the scenario's step range.
         */
        private boolean matchesLineFilter(Scenario scenario, Set<Integer> lines) {
            int scenarioLine = scenario.getLine();

            // Direct match on scenario line (for regular scenarios this is the Scenario: line,
            // for outline examples this is the Examples: table line)
            if (lines.contains(scenarioLine)) {
                return true;
            }

            // For outline examples, also check the Scenario Outline declaration line
            // and the specific example row line
            if (scenario.isOutlineExample() && scenario.getSection() != null
                    && scenario.getSection().isOutline()) {
                ScenarioOutline outline = scenario.getSection().getScenarioOutline();
                if (lines.contains(outline.getLine())) {
                    return true;
                }
                // Check if line matches this specific example row's line number
                for (ExamplesTable exTable : outline.getExamplesTables()) {
                    if (exTable.getLine() == scenarioLine) {
                        Table t = exTable.getTable();
                        int dataRowIndex = scenario.getExampleIndex() + 1; // +1 to skip header row
                        if (dataRowIndex < t.getRows().size()) {
                            int rowLine = t.getLineNumberForRow(dataRowIndex);
                            if (lines.contains(rowLine)) {
                                return true;
                            }
                        }
                        break;
                    }
                }
            }

            // Check if any line is within the scenario's step range
            // This handles clicking on a step within a scenario
            if (scenario.getSteps() != null && !scenario.getSteps().isEmpty()) {
                int firstStepLine = scenario.getSteps().get(0).getLine();
                int lastStepLine = scenario.getSteps().get(scenario.getSteps().size() - 1).getLine();
                int rangeStart = Math.min(scenarioLine, firstStepLine);

                for (int line : lines) {
                    if (line >= rangeStart && line <= lastStepLine) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Simple tag matching for call-by-tag syntax (e.g., call read('file.feature@tagname')).
         * Supports: @tagname, @name=value, ~@tagname (negation)
         * Does NOT filter by @ignore - allows calling @ignore scenarios explicitly.
         */
        private boolean matchesCallTag(Scenario scenario, String tagSelector) {
            List<Tag> tags = scenario.getTagsEffective();
            if (tags.isEmpty()) {
                return !tagSelector.startsWith("@");
            }

            // Check if it's a negation
            if (tagSelector.startsWith("~")) {
                String required = tagSelector.substring(1);
                for (Tag tag : tags) {
                    if (matchesTag(tag, required)) {
                        return false;
                    }
                }
                return true;
            }

            // Parse the selector (remove leading @)
            String selector = tagSelector.startsWith("@") ? tagSelector.substring(1) : tagSelector;

            // Check if any tag matches
            for (Tag tag : tags) {
                if (matchesTag(tag, selector)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Check if a tag matches a selector.
         * Selector can be: "tagname" or "name=value"
         */
        private boolean matchesTag(Tag tag, String selector) {
            int eqPos = selector.indexOf('=');
            if (eqPos == -1) {
                // Simple tag match: @tagname
                return tag.getName().equals(selector);
            } else {
                // Value tag match: @name=value
                String selectorName = selector.substring(0, eqPos);
                String selectorValue = selector.substring(eqPos + 1);
                return tag.getName().equals(selectorName) && tag.getValues().contains(selectorValue);
            }
        }
    }

    // ========== Resource Resolution ==========

    public Resource resolve(String path) {
        // V1 compatibility: handle 'this:' prefix for relative paths
        if (path.startsWith("this:")) {
            path = path.substring(5);  // Remove 'this:' prefix
        }
        return feature.getResource().resolve(path);
    }

    // ========== Accessors ==========

    public Suite getSuite() {
        return suite;
    }

    public Feature getFeature() {
        return feature;
    }

    public FeatureRuntime getCaller() {
        return caller;
    }

    public ScenarioRuntime getCallerScenario() {
        return callerScenario;
    }

    public boolean isSharedScope() {
        return sharedScope;
    }

    public Map<String, Object> getCallArg() {
        return callArg;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public ScenarioRuntime getLastExecuted() {
        return lastExecuted;
    }

    public FeatureResult getResult() {
        return result;
    }

    public ReentrantLock getCallOnceLock() {
        return callOnceLock;
    }

}
