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

import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class JobConfigBase<T> implements JobConfig<T> {

    protected static final Logger logger = LoggerFactory.getLogger(JobConfigBase.class);

    private final int executorCount;
    private final String host;
    private final int port;
    protected final List<String> sysPropKeys = new ArrayList(1);
    protected final List<String> envPropKeys = new ArrayList(1);

    protected String addOptions = "";
    protected String dockerImage = "ptrthomas/karate-chrome";

    public JobConfigBase(int executorCount, String host, int port) {
        this.executorCount = executorCount;
        this.host = host;
        this.port = port;
        sysPropKeys.add("karate.env");
    }

    public void setAddOptions(String addOptions) {
        this.addOptions = addOptions;
    }

    private ExecutorService executor;

    @Override
    public void onStart(String jobId, String jobUrl) {
        int count = getExecutorCount();
        if (count > 0) {
            executor = Executors.newFixedThreadPool(count);
            for (int i = 0; i < count; i++) {
                int index = i;
                String command = getExecutorCommand(jobId, jobUrl, index);
                if (command != null) {
                    executor.submit(() -> Command.execLine(null, command));
                }
            }
        }
    }

    @Override
    public void onStop() {
        if (executor != null) {
            executor.shutdown();
            int timeout = getTimeoutMinutes() * 60;
            logger.debug("called executor shutdown(), waiting");
            if (timeout == 0) {
                // if we don't wait enough time, docker processes can be left hanging
                timeout = 30;
            }
            try {
                executor.awaitTermination(timeout, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getExecutorCommand(String jobId, String jobUrl, int index) {
        String extra = StringUtils.isBlank(addOptions) ? "" : " " + addOptions;
        return "docker run --rm --cap-add=SYS_ADMIN -e KARATE_JOBURL=" + jobUrl + extra + " " + dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public void addSysPropKey(String key) {
        sysPropKeys.add(key);
    }

    public void addEnvPropKey(String key) {
        envPropKeys.add(key);
    }

    @Override
    public int getExecutorCount() {
        return executorCount;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public List<JobCommand> getStartupCommands() {
        return Collections.singletonList(new JobCommand("mvn test-compile"));
    }

    @Override
    public List<JobCommand> getShutdownCommands() {
        return Collections.singletonList(new JobCommand("supervisorctl shutdown"));
    }

    @Override
    public Map<String, String> getEnvironment() {
        Map<String, String> map = new HashMap(envPropKeys.size());
        for (String k : envPropKeys) {
            String v = StringUtils.trimToEmpty(System.getenv(k));
            if (!v.isEmpty()) {
                map.put(k, v);
            }
        }
        return map;
    }

}
