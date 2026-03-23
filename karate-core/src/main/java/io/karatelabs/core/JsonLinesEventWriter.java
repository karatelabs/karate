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
 * </pre>
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
        // Write to karate-json subfolder for consistency with other JSON outputs
        this.jsonlPath = outputDir.resolve(Suite.KARATE_JSON_SUBFOLDER).resolve(DEFAULT_FILENAME);
        this.env = env;
        this.threadCount = threadCount;
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

        // Skip step-level and HTTP events - too granular for JSONL stream.
        // Commercial listeners can capture HTTP data and add to step embeds,
        // which will appear in FEATURE_EXIT results.
        RunEventType type = event.getType();
        if (type == RunEventType.STEP_ENTER || type == RunEventType.STEP_EXIT
                || type == RunEventType.HTTP_ENTER || type == RunEventType.HTTP_EXIT) {
            return true;
        }

        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", event.getType().name());
            envelope.put("timeStamp", event.getTimeStamp());
            envelope.put("threadId", getThreadId(event));

            // Build data payload
            Map<String, Object> data = event.toJson();

            // Add schema version to SUITE_ENTER
            if (event.getType() == RunEventType.SUITE_ENTER) {
                data.put("schemaVersion", SCHEMA_VERSION);
                data.put("version", Globals.KARATE_VERSION);
                if (env != null && !env.isEmpty()) {
                    data.put("env", env);
                }
                data.put("threads", threadCount);
            }

            envelope.put("data", data);

            writeLine(Json.stringifyStrict(envelope));

        } catch (Exception e) {
            logger.warn("Failed to write event to JSONL: {}", e.getMessage());
        }

        return true;  // Never block execution
    }

    /**
     * Get the thread ID for the event envelope.
     * Suite-level events have null threadId.
     */
    private String getThreadId(RunEvent event) {
        RunEventType type = event.getType();
        if (type == RunEventType.SUITE_ENTER
                || type == RunEventType.SUITE_EXIT
                || type == RunEventType.PROGRESS) {
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
            writer.write(json);
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
