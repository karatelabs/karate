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

import com.intuit.karate.Http;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author pthomas3
 */
public class JobExecutorPulse extends TimerTask {

    private final JobExecutor executor;
    private final Http http;
    private static final int PERIOD = 15000; // fifteen seconds

    public JobExecutorPulse(JobExecutor executor) {
        this.executor = executor;
        http = Http.forUrl(executor.appender, executor.serverUrl);
    }

    public void start() {
        Timer timer = new Timer(true);
        timer.schedule(this, PERIOD, PERIOD);
    }

    @Override
    public void run() {
        String chunkId = executor.chunkId;
        JobMessage jm = new JobMessage("heartbeat");
        jm.setChunkId(chunkId);
        String jobId = executor.jobId;
        String executorId = executor.executorId;        
        JobExecutor.invokeServer(http, jobId, executorId, jm);
    }

}
