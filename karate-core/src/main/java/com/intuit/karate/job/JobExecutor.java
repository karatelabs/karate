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
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class JobExecutor {

    private final Http http;
    private final Logger logger;
    private final String workingDir;
    private final String jobId;
    private final String executorId;
    private final String uploadDir;
    private final Map<String, String> environment;
    private final List<JobCommand> shutdownCommands;

    private JobExecutor(String serverUrl) {
        http = Http.forUrl(LogAppender.NO_OP, serverUrl);
        http.config("lowerCaseResponseHeaders", "true");
        logger = new Logger();
        // download ============================================================
        JobMessage download = invokeServer(new JobMessage("download"));
        logger.info("download response: {}", download);
        jobId = download.getJobId();
        executorId = download.getExecutorId();
        workingDir = FileUtils.getBuildDir() + File.separator + jobId + "_" + executorId;
        byte[] bytes = download.getBytes();
        File file = new File(workingDir + ".zip");
        FileUtils.writeToFile(file, bytes);
        JobUtils.unzip(file, new File(workingDir));
        logger.info("download done: {}", workingDir);
        // init ================================================================
        JobMessage init = invokeServer(new JobMessage("init"));
        logger.info("init response: {}", init);
        uploadDir = workingDir + File.separator + init.get(JobContext.UPLOAD_DIR, String.class);
        List<JobCommand> startupCommands = init.getCommands("startupCommands");
        environment = init.get("environment", Map.class);
        executeCommands(startupCommands, environment);
        shutdownCommands = init.getCommands("shutdownCommands");
        logger.info("init done");
    }

    public static void run(String serverUrl) {
        JobExecutor je = new JobExecutor(serverUrl);
        je.loopNext();
        je.shutdown();
    }

    private File getWorkingDir(String relativePath) {
        if (relativePath == null) {
            return new File(workingDir);
        }
        return new File(relativePath + File.separator + workingDir);
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

    String chunkId = null;

    private void loopNext() {
        do {
            File uploadDirFile = new File(uploadDir);
            uploadDirFile.mkdirs();
            JobMessage req = new JobMessage("next")
                    .put(JobContext.UPLOAD_DIR, uploadDirFile.getAbsolutePath());
            req.setChunkId(chunkId);
            JobMessage res = invokeServer(req);
            if (res.is("stop")) {
                break;
            }
            chunkId = res.getChunkId();
            executeCommands(res.getCommands("preCommands"), environment);
            executeCommands(res.getCommands("mainCommands"), environment);
            stopBackgroundCommands();
            executeCommands(res.getCommands("postCommands"), environment);
            String zipBase = uploadDir + "_" + chunkId;
            File toZip = new File(zipBase);
            uploadDirFile.renameTo(toZip);
            File toUpload = new File(zipBase + ".zip");
            JobUtils.zip(toZip, toUpload);
            byte[] upload = toBytes(toUpload);
            req = new JobMessage("upload");
            req.setChunkId(chunkId);
            req.setBytes(upload);
            invokeServer(req);
        } while (true);
    }

    private void shutdown() {
        executeCommands(shutdownCommands, environment);
    }

    private void executeCommands(List<JobCommand> commands, Map<String, String> environment) {
        if (commands == null) {
            return;
        }
        for (JobCommand jc : commands) {
            String commandLine = jc.getCommand();
            File commandWorkingDir = getWorkingDir(jc.getWorkingPath());
            String[] args = Command.tokenize(commandLine);
            if (jc.isBackground()) {
                Logger silentLogger = new Logger(executorId);
                silentLogger.setAppendOnly(true);
                Command command = new Command(silentLogger, executorId, null, commandWorkingDir, args);
                command.setEnvironment(environment);
                command.start();
                backgroundCommands.add(command);
            } else {
                Command command = new Command(logger, executorId, null, commandWorkingDir, args);
                command.setEnvironment(environment);
                command.start();
                command.waitSync();
            }
        }
    }

    private JobMessage invokeServer(JobMessage req) {
        byte[] bytes = req.getBytes();
        ScriptValue body;
        String contentType;
        if (bytes != null) {
            contentType = "application/octet-stream";
            body = new ScriptValue(bytes);
        } else {
            contentType = "application/json";
            body = new ScriptValue(req.body);
        }
        Http.Response res = http.header(JobMessage.KARATE_METHOD, req.method)
                .header(JobMessage.KARATE_JOB_ID, jobId)
                .header(JobMessage.KARATE_EXECUTOR_ID, executorId)
                .header(JobMessage.KARATE_CHUNK_ID, req.getChunkId())
                .header("content-type", contentType).post(body);
        String method = StringUtils.trimToNull(res.header(JobMessage.KARATE_METHOD));
        contentType = StringUtils.trimToNull(res.header("content-type"));
        JobMessage jm;
        if (contentType != null && contentType.contains("octet-stream")) {
            jm = new JobMessage(method);
            jm.setBytes(res.bodyBytes().asType(byte[].class));
        } else {
            jm = new JobMessage(method, res.body().asMap());
        }
        jm.setJobId(StringUtils.trimToNull(res.header(JobMessage.KARATE_JOB_ID)));
        jm.setExecutorId(StringUtils.trimToNull(res.header(JobMessage.KARATE_EXECUTOR_ID)));
        jm.setChunkId(StringUtils.trimToNull(res.header(JobMessage.KARATE_CHUNK_ID)));
        return jm;
    }

}
