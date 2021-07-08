/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.Constants;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.StringUtils;
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.http.ResourceType;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author pthomas3
 */
public class MavenJobConfig extends JobConfigBase<ScenarioRuntime> {

    public MavenJobConfig(int executorCount, String host, int port) {
        super(executorCount, host, port);
    }

    @Override
    public List<JobCommand> getMainCommands(JobChunk<ScenarioRuntime> chunk) {
        Scenario scenario = chunk.getValue().scenario;
        String path = scenario.getFeature().getResource().getPrefixedPath();
        int line = scenario.getLine();
        String temp = "mvn exec:java -Dexec.mainClass=com.intuit.karate.Main -Dexec.classpathScope=test"
                + " \"-Dexec.args=" + path + ":" + line + "\"";
        for (String k : sysPropKeys) {
            String v = StringUtils.trimToEmpty(System.getProperty(k));
            if (!v.isEmpty()) {
                temp = temp + " -D" + k + "=" + v;
            }
        }
        return Collections.singletonList(new JobCommand(temp));
    }

    @Override
    public ScenarioRuntime handleUpload(JobChunk<ScenarioRuntime> chunk, File upload) {
        ScenarioRuntime runtime = chunk.getValue();
        File jsonFile = JobUtils.getFirstFileMatching(upload, n -> n.endsWith(Constants.KARATE_JSON_SUFFIX));
        if (jsonFile == null) {
            logger.warn("no karate json found in job executor result");
            return runtime;
        }
        String json = FileUtils.toString(jsonFile);
        Map<String, Object> map = Json.of(json).asMap();
        FeatureResult fr = FeatureResult.fromKarateJson(runtime.featureRuntime.suite.workingDir, map);
        if (fr.getScenarioResults().isEmpty()) {
            logger.warn("executor feature result is empty");
            return runtime;
        }
        Optional<ScenarioResult> optional = fr.getScenarioResults().stream().filter(sr -> !sr.getStepResults().isEmpty()).findFirst();
        if (!optional.isPresent()) {
            logger.warn("executor scenario result is empty");
            return runtime;            
        }
        ScenarioResult sr = optional.get();
        sr.setExecutorName(chunk.getExecutorId());
        sr.setStartTime(chunk.getStartTime());
        sr.setEndTime(System.currentTimeMillis());
        synchronized (runtime.featureRuntime) {
            runtime.featureRuntime.result.addResult(sr);
        }
        String reportDir = runtime.featureRuntime.suite.reportDir;
        for (File file : fr.getAllEmbedFiles()) {
            File dest = new File(reportDir + File.separator + file.getName());
            FileUtils.copy(file, dest);
        }
        File videoFile = JobUtils.getFirstFileMatching(upload, n -> n.endsWith("karate.mp4"));
        if (videoFile != null) {
            StepResult stepResult = sr.addFakeStepResult("[video]", null);
            Embed embed = runtime.saveToFileAndCreateEmbed(FileUtils.toBytes(videoFile), ResourceType.MP4);
            stepResult.addEmbed(embed);
        }
        return runtime;
    }

}
