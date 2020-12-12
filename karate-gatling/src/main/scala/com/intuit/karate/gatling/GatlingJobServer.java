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
import com.intuit.karate.job.JobConfig;
import com.intuit.karate.job.JobManager;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class GatlingJobServer extends JobManager {

    public GatlingJobServer(JobConfig config) {
        super(config, "target/gatling");
        // TODO
    }

    @Override
    public void handleUpload(File upload, String executorId, String chunkId) {
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
    }

}
