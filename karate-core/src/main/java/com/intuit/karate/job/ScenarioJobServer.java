/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.job;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.ScenarioResult;
import static com.intuit.karate.job.JobServer.LOGGER;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author pthomas3
 */
public class ScenarioJobServer extends JobServer {

    protected final List<FeatureScenarios> FEATURE_QUEUE = new ArrayList();
    protected final Map<String, ChunkResult> CHUNK_RESULTS = new HashMap();
    private final AtomicInteger chunkCounter = new AtomicInteger();

    public ScenarioJobServer(JobConfig config, String reportDir) {
        super(config, reportDir);
    }

    @Override
    public void addFeature(ExecutionContext exec, List<ScenarioExecutionUnit> units, Runnable onComplete) {
        Logger logger = new Logger();
        List<Scenario> selected = new ArrayList(units.size());
        for (ScenarioExecutionUnit unit : units) {
            if (FeatureExecutionUnit.isSelected(exec.featureContext, unit.scenario, logger)) {
                selected.add(unit.scenario);
            }
        }
        if (selected.isEmpty()) {
            onComplete.run();
        } else {
            FeatureScenarios fs = new FeatureScenarios(exec, selected, onComplete);
            FEATURE_QUEUE.add(fs);
        }
    }

    @Override
    public ChunkResult getNextChunk(String executorId) {
        synchronized (FEATURE_QUEUE) {
            if (FEATURE_QUEUE.isEmpty()) {
                return null;
            } else {
                FeatureScenarios feature = FEATURE_QUEUE.get(0);
                Scenario scenario = feature.scenarios.remove(0);
                if (feature.scenarios.isEmpty()) {
                    FEATURE_QUEUE.remove(0);
                }
                LOGGER.info("features queued: {}", FEATURE_QUEUE);
                ChunkResult chunk = new ChunkResult(feature, scenario);
                String chunkId = chunkCounter.incrementAndGet() + "";
                chunk.setChunkId(chunkId);
                chunk.setStartTime(System.currentTimeMillis());
                feature.chunks.add(chunk);
                CHUNK_RESULTS.put(chunkId, chunk);
                return chunk;
            }
        }
    }

    @Override
    public void handleUpload(File upload, String executorId, String chunkId) {
        File jsonFile = getFirstFileWithExtension(upload, "json");
        if (jsonFile == null) {
            return;
        }
        String json = FileUtils.toString(jsonFile);
        File videoFile = getFirstFileWithExtension(upload, "mp4");
        List<Map<String, Object>> list = JsonUtils.toJsonDoc(json).read("$[0].elements");
        synchronized (CHUNK_RESULTS) {
            ChunkResult cr = CHUNK_RESULTS.remove(chunkId);
            LOGGER.info("chunk complete: {}, remaining: {}", chunkId, CHUNK_RESULTS.keySet());
            if (cr == null) {
                LOGGER.error("could not find chunk: {}", chunkId);
                return;
            }
            ScenarioResult sr = new ScenarioResult(cr.scenario, list, true);
            sr.setStartTime(cr.getStartTime());
            sr.setEndTime(System.currentTimeMillis());
            sr.setThreadName(executorId);
            cr.setResult(sr);
            if (videoFile != null) {
                File dest = new File(FileUtils.getBuildDir() + File.separator + chunkId + ".mp4");
                FileUtils.copy(videoFile, dest);
                sr.appendEmbed(Embed.forVideoFile("../" + dest.getName()));
            }
            if (cr.parent.isComplete()) {
                LOGGER.info("feature complete, calling onComplete(): {}", cr.parent);
                cr.parent.onComplete();
            }
        }
    }

}
