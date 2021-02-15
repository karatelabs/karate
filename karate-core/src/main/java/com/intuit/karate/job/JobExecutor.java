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
import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.Response;
import com.intuit.karate.shell.Command;
import com.intuit.karate.shell.FileLogAppender;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author pthomas3
 */
public class JobExecutor {

    protected final String serverUrl;
    private final Http http;
    private final Logger logger;
    protected final LogAppender appender;
    private final String workingDir;
    protected final String jobId;
    protected final String executorId;
    private final String executorDir;
    private final Map<String, String> environment;
    private final List<JobCommand> shutdownCommands;

    protected AtomicReference<String> chunkId = new AtomicReference();

    private JobExecutor(String serverUrl) {
        this.serverUrl = serverUrl;
        String targetDir = FileUtils.getBuildDir();
        appender = new FileLogAppender(new File(targetDir + File.separator + "karate-executor.log"));
        logger = new Logger();
        logger.setAppender(appender);
        if (!Command.waitForHttp(serverUrl + "/healthcheck")) {
            logger.error("unable to connect to server, aborting");
            System.exit(1);
        }
        http = Http.to(serverUrl);
        http.configure("lowerCaseResponseHeaders", "true");
        // download ============================================================
        JobMessage download = invokeServer(new JobMessage("download"));
        logger.info("download response: {}", download);
        jobId = download.getJobId();
        executorId = download.getExecutorId();
        workingDir = FileUtils.getBuildDir() + File.separator + jobId + "_" + executorId;
        byte[] bytes = download.getBytes();
        File file = new File(workingDir + ".zip");
        FileUtils.writeToFile(file, bytes);
        environment = new HashMap(System.getenv());
        try {
            JobUtils.unzip(file, new File(workingDir));
            logger.info("download done: {}", workingDir);
            // init ================================================================
            JobMessage init = invokeServer(new JobMessage("init").put("log", appender.collect()));
            logger.info("init response: {}", init);
            executorDir = workingDir + File.separator + init.get("executorDir");
            List<JobCommand> startupCommands = init.getCommands("startupCommands");
            environment.putAll(init.get("environment"));
            executeCommands(startupCommands, environment);
            shutdownCommands = init.getCommands("shutdownCommands");
            logger.info("init done, executor dir: {}", executorDir);
        } catch (Exception e) {
            reportErrorAndExit(this, e);
            // we will never reach here because of a System.exit()
            throw new RuntimeException(e);
        }
    }

    public static void run(String serverUrl) {
        JobExecutor je = new JobExecutor(serverUrl);
        JobExecutorPulse pulse = new JobExecutorPulse(je);
        pulse.start();
        try {
            je.loopNext();
            je.shutdown();
        } catch (Exception e) {
            reportErrorAndExit(je, e);
        }
    }

    private static void reportErrorAndExit(JobExecutor je, Exception e) {
        je.logger.error("{}", e.getMessage());
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        try {
            je.invokeServer(new JobMessage("error").put("log", sw.toString()));
        } catch (Exception ee) {
            je.logger.error("attempt to report error failed: {}", ee.getMessage());
        }
    }

    private final List<Command> backgroundCommands = new ArrayList(1);

    private void stopBackgroundCommands() {
        while (!backgroundCommands.isEmpty()) {
            Command command = backgroundCommands.remove(0);
            command.close(false);
            command.waitSync();
            // logger.debug("killed background job: \n{}\n", command.getAppender().collect());
        }
    }

    private byte[] toBytes(File file) {
        try {
            InputStream is = new FileInputStream(file);
            return FileUtils.toBytes(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loopNext() {
        do {
            File executorDirFile = new File(executorDir);
            executorDirFile.mkdirs();
            JobMessage req = new JobMessage("next").put("executorDir", executorDirFile.getAbsolutePath());
            JobMessage res = invokeServer(req);
            if (res.is("stop")) {
                logger.info("stop received, shutting down");
                break;
            }
            chunkId.set(res.getChunkId());
            executeCommands(res.getCommands("preCommands"), environment);
            executeCommands(res.getCommands("mainCommands"), environment);
            stopBackgroundCommands();
            executeCommands(res.getCommands("postCommands"), environment);
            String log = appender.collect();
            File logFile = new File(executorDir + File.separator + "karate.log");
            FileUtils.writeToFile(logFile, log);
            String zipBase = executorDir + "_" + chunkId.get();
            File toZip = new File(zipBase);
            if (!executorDirFile.renameTo(toZip)) {
                logger.warn("failed to rename old executor dir: {}", executorDirFile);
            }
            File toUpload = new File(zipBase + ".zip");
            JobUtils.zip(toZip, toUpload);
            byte[] upload = toBytes(toUpload);
            req = new JobMessage("upload");
            req.setBytes(upload);
            invokeServer(req);
        } while (true);
    }

    private void shutdown() {
        stopBackgroundCommands();
        executeCommands(shutdownCommands, environment);
        logger.info("shutdown complete");
    }

    private void executeCommands(List<JobCommand> commands, Map<String, String> environment) {
        if (commands == null) {
            return;
        }
        for (JobCommand jc : commands) {
            String commandLine = jc.getCommand();
            String workingPath = jc.getWorkingPath();
            File commandWorkingDir;
            if (workingPath == null) {
                commandWorkingDir = new File(workingDir);
            } else {
                commandWorkingDir = new File(workingPath + File.separator + workingDir);
            }
            String[] args = Command.tokenize(commandLine);
            if (FileUtils.isOsWindows()) {
                args = Command.prefixShellArgs(args);
            }
            if (jc.isBackground()) {
                Logger silentLogger = new Logger(executorId);
                silentLogger.setAppendOnly(true);
                Command command = new Command(false, silentLogger, executorId, null, commandWorkingDir, args);
                command.setEnvironment(environment);
                command.start();
                backgroundCommands.add(command);
            } else {
                Command command = new Command(false, logger, executorId, null, commandWorkingDir, args);
                command.setEnvironment(environment);
                command.start();
                command.waitSync();
            }
        }
    }

    private JobMessage invokeServer(JobMessage req) {
        req.setJobId(jobId);
        req.setExecutorId(executorId);
        req.setChunkId(chunkId.get());
        return invokeServer(http, req);
    }

    protected static JobMessage invokeServer(Http http, JobMessage req) {
        byte[] bytes = req.getBytes();
        String contentType;
        if (bytes != null) {
            contentType = ResourceType.BINARY.contentType;
        } else {
            contentType = ResourceType.JSON.contentType;
            bytes = JsonUtils.toJsonBytes(req.getBody());
        }
        Json json = Json.object();
        json.set("method", req.method);
        if (req.getJobId() != null) {
            json.set("jobId", req.getJobId());
        }
        if (req.getExecutorId() != null) {
            json.set("executorId", req.getExecutorId());
        }
        if (req.getChunkId() != null) {
            json.set("chunkId", req.getChunkId());
        }
        Response res = http.header(JobManager.KARATE_JOB_HEADER, json.toString())
                .header("content-type", contentType).post(bytes);
        String jobHeader = res.getHeader(JobManager.KARATE_JOB_HEADER);
        JobMessage jm = JobManager.toJobMessage(jobHeader);
        ResourceType rt = res.getResourceType();
        if (rt != null && rt.isBinary()) {
            jm.setBytes(res.getBody());
        } else {
            jm.setBody(res.json().asMap());
        }
        return jm;
    }

}
