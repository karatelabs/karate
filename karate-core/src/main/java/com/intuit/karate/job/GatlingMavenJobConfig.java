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
import com.intuit.karate.StringUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class GatlingMavenJobConfig extends JobConfigBase<Integer> {

    private String mainCommand = "mvn gatling:test";
    private String executorDir = "target/gatling";

    public GatlingMavenJobConfig(int executorCount, String host, int port) {
        super(executorCount, host, port);
    }

    @Override
    public List<Integer> getInitialChunks() {
        int count = getExecutorCount();
        List<Integer> list = new ArrayList(count);
        for (int i = 0; i < count; i++) {
            list.add(i);
        }
        return list;
    }

    public void setMainCommand(String mainCommand) {
        this.mainCommand = mainCommand;
    }

    @Override
    public String getExecutorDir() {
        return executorDir;
    }

    public void setExecutorDir(String executorDir) {
        this.executorDir = executorDir;
    }

    @Override
    public List<JobCommand> getMainCommands(JobChunk jc) {
        String temp = mainCommand;
        for (String k : sysPropKeys) {
            String v = StringUtils.trimToEmpty(System.getProperty(k));
            if (!v.isEmpty()) {
                temp = temp + " -D" + k + "=" + v;
            }
        }
        return Collections.singletonList(new JobCommand(temp));
    }

    @Override
    public Integer handleUpload(JobChunk<Integer> jc, File upload) {
        String karateLog = upload.getPath() + File.separator + "karate.log";
        File karateLogFile = new File(karateLog);
        if (karateLogFile.exists()) {
            karateLogFile.renameTo(new File(karateLog + ".txt"));
        }
        String gatlingReportDir = "target" + File.separator + "reports" + File.separator;
        new File(gatlingReportDir).mkdirs();
        File[] dirs = upload.listFiles();
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                File file = JobUtils.getFirstFileWithExtension(dir, "log");
                if (file != null) {
                    FileUtils.copy(file, new File(gatlingReportDir + "simulation_" + jc.getId() + ".log"));
                }
            }
        }
        return jc.getValue();
    }

}
