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

import io.karatelabs.common.Json;
import io.karatelabs.gherkin.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A {@link RunListener} that streams events to a JSON Lines (.jsonl) file.
 * <p>
 * This writer outputs events in the standard envelope format:
 * <pre>
 * {"type":"SUITE_ENTER","timeStamp":1703500000000,"threadId":null,"data":{...}}
 * {"type":"FEATURE_ENTER","timeStamp":1703500000010,"threadId":"worker-1","data":{...}}
 * {"type":"SCENARIO_ENTER","timeStamp":1703500000020,"threadId":"worker-1","data":{...}}
 * {"type":"SCENARIO_EXIT","timeStamp":1703500000100,"threadId":"worker-1","data":{...}}
 * {"type":"FEATURE_EXIT","timeStamp":1703500000200,"threadId":"worker-1","data":{...}}
 * {"type":"SUITE_EXIT","timeStamp":1703500010000,"threadId":null,"data":{...}}
 * {"type":"RUN_COMPLETE","timeStamp":1703500010200,"threadId":null,"data":{...}}
 * </pre>
 * Every line also carries {@code runId} and, where they apply, {@code executionIndex}, {@code parentExecutionIndex},
 * {@code parentStepLine} and {@code sequence} — see {@link #envelope(RunEvent)}.
 * <p>
 * Use cases:
 * <ul>
 *   <li>Real-time test dashboards - stream lightweight events as tests run</li>
 *   <li>CI/CD integration - parse events for build status, notifications</li>
 *   <li>Report aggregation - combine JSONL from multiple runs/machines</li>
 *   <li>IDE integration - SCENARIO_ENTER/EXIT events for test runners</li>
 * </ul>
 */
public class JsonLinesEventWriter implements RunListener, Closeable {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    public static final String DEFAULT_FILENAME = "karate-events.jsonl";
    public static final String SCHEMA_VERSION = "1";

    private final Path jsonlPath;
    private final String env;
    private final int threadCount;
    private final UnaryOperator<String> mask;
    private BufferedWriter writer;
    private volatile boolean closed = false;

    /**
     * Create a new JSON Lines event writer.
     *
     * @param outputDir   the directory to write the file
     * @param env         the karate environment (may be null)
     * @param threadCount the number of parallel threads
     */
    public JsonLinesEventWriter(Path outputDir, String env, int threadCount) {
        this(outputDir, env, threadCount, null);
    }

    /** @param mask applied to each line before it is written ({@code Runner.Builder.outputMask}); may be null */
    public JsonLinesEventWriter(Path outputDir, String env, int threadCount, UnaryOperator<String> mask) {
        // Write to karate-json subfolder for consistency with other JSON outputs
        this.jsonlPath = outputDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve(DEFAULT_FILENAME);
        this.env = env;
        this.threadCount = threadCount;
        this.mask = mask;
    }

    /**
     * Initialize the writer. Must be called before events are fired.
     */
    public void init() throws IOException {
        Files.createDirectories(jsonlPath.getParent());
        writer = Files.newBufferedWriter(jsonlPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        logger.debug("JSONL event stream started: {}", jsonlPath);
    }

    @Override
    public boolean onEvent(RunEvent event) {
        if (closed || writer == null) {
            return true;
        }

        // Skip step-level, HTTP and assertion events - too granular for JSONL stream.
        // Commercial listeners can capture HTTP / match data and add to step embeds,
        // which will appear in FEATURE_EXIT results.
        RunEventType type = event.getType();
        if (type == RunEventType.STEP_ENTER || type == RunEventType.STEP_EXIT || type == RunEventType.STEP_LOG
                || type == RunEventType.HTTP_ENTER || type == RunEventType.HTTP_EXIT
                || type == RunEventType.MATCH_EXIT) {
            return true;
        }

        try {
            Map<String, Object> envelope = envelope(event);

            // Add schema version to SUITE_ENTER
            if (event.getType() == RunEventType.SUITE_ENTER) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) envelope.get("data");
                data.put("schemaVersion", SCHEMA_VERSION);
                data.put("version", Globals.KARATE_VERSION);
                if (env != null && !env.isEmpty()) {
                    data.put("env", env);
                }
                data.put("threads", threadCount);
            }

            writeLine(Json.stringifyStrict(envelope));

        } catch (Exception e) {
            logger.warn("Failed to write event to JSONL: {}", e.getMessage());
        }

        return true;  // Never block execution
    }

    /**
     * The wire form of one event: {@code type}, {@code timeStamp}, {@code threadId}, {@code runId},
     * {@code sequence} (under the IDE event profile), {@code executionIndex} (the scenario an event belongs
     * to), {@code parentExecutionIndex} and {@code parentStepLine} (the scenario and step that called it,
     * for a nested feature, outline or scenario and everything inside one), then {@code data}. Shared with
     * any listener that streams events elsewhere.
     */
    public static Map<String, Object> envelope(RunEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", event.getType().name());
        envelope.put("timeStamp", event.getTimeStamp());
        envelope.put("threadId", getThreadId(event));
        ScenarioRuntime own = scenarioOf(event);
        FeatureRuntime fr = own != null ? own.getFeatureRuntime() : featureOf(event);
        Suite suite = fr != null ? fr.getSuite()
                : event instanceof SuiteRunEvent sre ? sre.source()
                : event instanceof ProgressRunEvent pre ? pre.suite() : null;
        if (suite != null) {
            envelope.put("runId", suite.runId);
        }
        if (event.getSequence() > 0) {
            envelope.put("sequence", event.getSequence());
        }
        if (own != null && own.getResult().getExecutionIndex() > 0) {
            envelope.put("executionIndex", own.getResult().getExecutionIndex());
        }
        ScenarioRuntime caller = fr != null ? fr.getCallerScenario() : null;
        if (caller != null) {
            envelope.put("parentExecutionIndex", caller.getResult().getExecutionIndex());
            Step step = caller.getCurrentStep();
            if (step != null) {
                envelope.put("parentStepLine", step.getLine());
            }
        }
        envelope.put("data", event.toJson());
        return envelope;
    }

    /** The scenario an event belongs to; null for a feature-, outline- or suite-level event. */
    private static ScenarioRuntime scenarioOf(RunEvent event) {
        return switch (event) {
            case ScenarioRunEvent e -> e.source();
            case StepRunEvent e -> e.scenarioRuntime();
            case StepLogRunEvent e -> e.scenarioRuntime();
            case HttpRunEvent e -> e.scenarioRuntime();
            case MatchRunEvent e -> e.scenarioRuntime();
            case ErrorRunEvent e -> e.scenarioRuntime();
            default -> null;
        };
    }

    private static FeatureRuntime featureOf(RunEvent event) {
        return switch (event) {
            case FeatureRunEvent e -> e.source();
            case OutlineRunEvent e -> e.source();
            default -> null;
        };
    }

    /**
     * Get the thread ID for the event envelope.
     * Suite-level events have null threadId.
     */
    private static String getThreadId(RunEvent event) {
        RunEventType type = event.getType();
        if (type == RunEventType.SUITE_ENTER
                || type == RunEventType.SUITE_EXIT
                || type == RunEventType.PROGRESS
                || type == RunEventType.RUN_CANCEL
                || type == RunEventType.RUN_COMPLETE) {
            return null;
        }
        Thread thread = Thread.currentThread();
        String name = thread.getName();
        if (name == null || name.isEmpty() || "main".equals(name)) {
            return "thread-" + thread.threadId();
        }
        return name;
    }

    /**
     * Thread-safe write to JSON Lines file.
     */
    private synchronized void writeLine(String json) throws IOException {
        if (writer != null && !closed) {
            writer.write(mask == null ? json : mask.apply(json));
            writer.newLine();
            writer.flush();  // Ensure atomic writes for tailing
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        if (writer != null) {
            writer.close();
            writer = null;
            logger.info("JSONL event stream written to: {}", jsonlPath);
        }
    }

    /**
     * Get the path to the JSON Lines file.
     */
    public Path getJsonlPath() {
        return jsonlPath;
    }

}
