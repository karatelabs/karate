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
import com.intuit.karate.shell.Command;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class JobExecutor {

    private final Http http;
    private final String id;
    private final Logger logger;
    private final String basePath;

    private JobMessage invokeServer(JobMessage req) {
        byte[] bytes = req.getBytes();
        Http.Response res;
        if (bytes != null) {
            res = http.header(JobMessage.KARATE_METHOD, req.method)
                    .header(JobMessage.KARATE_EXECUTOR_ID, id)
                    .header(JobMessage.KARATE_CHUNK_ID, req.getChunkId())
                    .header("content-type", "application/octet-stream").post(bytes);
        } else {
            res = http.header(JobMessage.KARATE_METHOD, req.method)
                    .header(JobMessage.KARATE_EXECUTOR_ID, id)
                    .header(JobMessage.KARATE_CHUNK_ID, req.getChunkId())
                    .header("content-type", "application/json").post(req.body);
        }
        String method = res.header(JobMessage.KARATE_METHOD);
        String chunkId = res.header(JobMessage.KARATE_CHUNK_ID);
        String contentType = res.header("content-type");
        JobMessage jm;
        if (contentType != null && contentType.contains("octet-stream")) {
            jm = new JobMessage(method);
            jm.setBytes(res.bodyBytes().asType(byte[].class));
        } else {
            jm = new JobMessage(method, res.body().asMap());
        }
        jm.setChunkId(chunkId);
        return jm;
    }

    public static void run(String serverUrl, String id) {
        JobExecutor je = new JobExecutor(serverUrl, id);
        je.run();
    }

    private File getWorkingDir(String workingPath) {
        if (workingPath == null) {
            return new File(basePath);
        }
        return new File(basePath + File.separator + workingPath);
    }

    private JobExecutor(String serverUrl, String id) {
        http = Http.forUrl(LogAppender.NO_OP, serverUrl);
        http.config("lowerCaseResponseHeaders", "true");
        if (id == null) {
            id = System.currentTimeMillis() + "_executor";
        }
        this.id = id;
        logger = new Logger();
        basePath = FileUtils.getBuildDir() + File.separator + "executor_" + id;
    }

    private final List<Command> backgroundCommands = new ArrayList(1);

    private void stopBackgroundCommands() {
        while (!backgroundCommands.isEmpty()) {
            Command command = backgroundCommands.remove(0);
            command.close();
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

    private void run() {
        // download ============================================================
        JobMessage req = new JobMessage("download");
        JobMessage res = invokeServer(req);
        logger.info("download response: {}", res);
        byte[] bytes = res.getBytes();
        File file = new File(basePath + ".zip");
        FileUtils.writeToFile(file, bytes);
        JobUtils.unzip(file, new File(basePath));
        logger.info("download extracted to : {}", basePath);
        // init ================================================================
        req = new JobMessage("init");
        res = invokeServer(req);
        logger.info("init response: {}", res);
        String reportPath = res.get("reportPath", String.class);
        List<JobCommand> initCommands = res.getCommands("initCommands");
        Map<String, String> environment = res.get("environment", Map.class);
        executeCommands(initCommands, environment);
        logger.info("completed init");
        // next ================================================================
        req = new JobMessage("next"); // first
        do {
            res = invokeServer(req);
            if (res.is("stop")) {
                break;
            }
            String chunkId = res.getChunkId();
            executeCommands(res.getCommands("preCommands"), environment);
            executeCommands(res.getCommands("mainCommands"), environment);
            stopBackgroundCommands();
            executeCommands(res.getCommands("postCommands"), environment);
            File toRename = new File(basePath + File.separator + reportPath);
            String zipBase = basePath + File.separator + FileUtils.getBuildDir() + File.separator + "chunk_" + chunkId;
            File toZip = new File(zipBase);
            toRename.renameTo(toZip);
            File toUpload = new File(zipBase + ".zip");
            JobUtils.zip(toZip, toUpload);
            byte[] upload = toBytes(toUpload);
            req = new JobMessage("upload");
            req.setChunkId(chunkId);
            req.setBytes(upload);
            invokeServer(req);
            req = new JobMessage("next");
            req.setChunkId(chunkId);
        } while (true);
    }

    private void executeCommands(List<JobCommand> commands, Map<String, String> environment) {
        if (commands == null) {
            return;
        }
        for (JobCommand jc : commands) {
            String commandLine = jc.getCommand();
            File workingDir = getWorkingDir(jc.getWorkingPath());
            String[] args = Command.tokenize(commandLine);
            if (jc.isBackground()) {
                Logger silentLogger = new Logger(id);
                silentLogger.setAppendOnly(true);
                Command command = new Command(silentLogger, id, null, workingDir, args);
                command.setEnvironment(environment);
                command.start();
                backgroundCommands.add(command);
            } else {
                Command command = new Command(logger, id, null, workingDir, args);
                command.setEnvironment(environment);
                command.start();
                command.waitSync();
            }
        }
    }

}
