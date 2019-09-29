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
package com.intuit.karate.gatling;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.job.ChunkResult;
import com.intuit.karate.job.JobConfig;
import com.intuit.karate.job.JobServer;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author pthomas3
 */
public class GatlingJobServer extends JobServer {

    private final Set<String> executors = new HashSet();
    private final Set<String> completed = new HashSet();

    public GatlingJobServer(JobConfig config) {
        super(config, "target/gatling");
    }

    @Override
    public void addFeature(ExecutionContext exec, List<ScenarioExecutionUnit> units, Runnable onComplete) {
        // TODO not applicable
    }

    @Override
    public synchronized ChunkResult getNextChunk(String executorId) {
        if (executors.contains(executorId)) {
            if (completed.size() >= executors.size()) {
                stop();
            }
            return null;
        }
        executors.add(executorId);
        ChunkResult chunk = new ChunkResult(null, null);
        chunk.setChunkId(executorId);
        return chunk;
    }

    @Override
    public synchronized void handleUpload(File upload, String executorId, String chunkId) {
        String karateLog = upload.getPath() + File.separator + "karate.log";
        File karateLogFile = new File(karateLog);
        if (karateLogFile.exists()) {
            karateLogFile.renameTo(new File(karateLog + ".txt"));
        }
        String gatlingReportDir = "target" + File.separator + "reports" + File.separator;
        File[] dirs = upload.listFiles();
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                File file = getFirstFileWithExtension(dir, "log");
                if (file != null) {
                    FileUtils.copy(file, new File(gatlingReportDir + "simulation_" + chunkId + ".log"));
                }
            }
        }
        completed.add(executorId);
    }

}
