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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.Request;
import com.intuit.karate.http.ResourceType;
import com.intuit.karate.http.Response;
import com.intuit.karate.http.ServerHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JobManager implements ServerHandler {

    protected static final Logger logger = LoggerFactory.getLogger(JobManager.class);

    private final JobConfig config;
    private final String basePath;
    private final File ZIP_FILE;
    public final String jobId;
    private final String reportDir;
    private final AtomicInteger executorCounter = new AtomicInteger(1);

    public final HttpServer server;
    public final String jobUrl;

    public JobManager(JobConfig config, String reportDir) {
        this.config = config;
        this.reportDir = reportDir;
        jobId = System.currentTimeMillis() + "";
        basePath = FileUtils.getBuildDir() + File.separator + jobId;
        ZIP_FILE = new File(basePath + ".zip");
        JobUtils.zip(new File(config.getSourcePath()), ZIP_FILE);
        logger.info("created zip archive: {}", ZIP_FILE);
        server = new HttpServer(config.getPort(), this);
        jobUrl = "http://" + config.getHost() + ":" + server.getPort();
    }

    public void handleUpload(File upload, String executorId, String chunkId) {
        synchronized (chunks) {
            JobChunk chunk = chunks.get(chunkId);
            CompletableFuture<JobChunk> future = futures.get(chunkId);
            future.complete(chunk);
            logger.debug("completed: {}", chunkId);
        }
        File jsonFile = getFirstFileWithExtension(upload, "json");
        if (jsonFile == null) {
            return;
        }
        String json = FileUtils.toString(jsonFile);
        File videoFile = getFirstFileWithExtension(upload, "mp4");
        List<Map<String, Object>> list = Json.of(json).get("$[0].elements");
        Scenario scenario = null; // TODO
        ScenarioResult sr = new ScenarioResult(scenario, list, true);
        // sr.setStartTime(cr.getStartTime());
        sr.setEndTime(System.currentTimeMillis());
        sr.setThreadName(executorId);
        if (videoFile != null) {
            File dest = new File(FileUtils.getBuildDir() + File.separator + chunkId + ".mp4");
            FileUtils.copy(videoFile, dest);
            sr.appendEmbed(Embed.videoFile("../" + dest.getName()));
        }
    }

    public JobChunk getNextChunk(String executorId) {
        return queue.poll();
    }

    private final Map<String, JobChunk> chunks = new HashMap();
    private final Map<String, CompletableFuture<JobChunk>> futures = new HashMap();
    private final ArrayBlockingQueue<JobChunk> queue = new ArrayBlockingQueue(1);

    public CompletableFuture<JobChunk> addChunk(JobChunk chunk) {
        try {
            logger.debug("waiting for queue: {}", chunk.getChunkId());
            queue.put(chunk);
            logger.debug("queue put: {}", chunk.getChunkId());
            synchronized (chunks) {
                chunks.put(chunk.getChunkId(), chunk);
                CompletableFuture<JobChunk> future = new CompletableFuture();
                futures.put(chunk.getChunkId(), future);
                return future;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response handle(Request request) {
        if (!request.getMethod().equals("POST")) {
            return errorResponse(request + " not supported");
        }
        String jobHeader = request.getHeader(JobMessage.KARATE_JOB);
        JobMessage req = toJobMessage(jobHeader);
        if (req.method == null) {
            return errorResponse(JobMessage.KARATE_METHOD + " param required");
        }
        ResourceType rt = request.getResourceType();
        if (rt != null && rt.isBinary()) {
            req.setBytes(request.getBody());
        } else {
            req.setBody((Map) request.getBodyConverted());
        }
        JobMessage res = handle(req);
        Response response = new Response(200);
        Json json = Json.object();
        json.set("method", res.method);
        json.set("jobId", jobId);
        if (req.getExecutorId() != null) {
            json.set("executorId", req.getExecutorId());
        }
        if (res.getChunkId() != null) {
            json.set("chunkId", res.getChunkId());
        }
        response.setHeader(JobMessage.KARATE_JOB, json.toString());
        if (res.getBytes() != null) {
            response.setBody(res.getBytes());
            response.setContentType(ResourceType.BINARY.contentType);
        } else if (res.getBody() != null) {
            byte[] bytes = JsonUtils.toJsonBytes(res.getBody());
            response.setBody(bytes);
            response.setContentType(ResourceType.JSON.contentType);
        }
        return response;
    }

    private Response errorResponse(String message) {
        Response response = new Response(400);
        response.setBody(message);
        return response;
    }

    public static JobMessage toJobMessage(String value) {
        Json json = Json.of(value);
        String method = json.getOptional("method");
        JobMessage jm = new JobMessage(method);
        jm.setJobId(json.getOptional("jobId"));
        jm.setExecutorId(json.getOptional("executorId"));
        jm.setChunkId(json.getOptional("chunkId"));
        return jm;
    }

    private JobMessage handle(JobMessage jm) {
        String method = jm.method;
        switch (method) {
            case "error":
                dumpLog(jm);
                return new JobMessage("error");
            case "heartbeat":
                logger.info("hearbeat: {}", jm);
                return new JobMessage("heartbeat");
            case "download":
                logger.info("download: {}", jm);
                JobMessage download = new JobMessage("download");
                download.setBytes(getDownload());
                int executorId = executorCounter.getAndIncrement();
                download.setExecutorId(executorId + "");
                return download;
            case "init":
                logger.info("init: {}", jm);
                JobMessage init = new JobMessage("init");
                init.put("startupCommands", config.getStartupCommands());
                init.put("shutdownCommands", config.getShutdownCommands());
                init.put("environment", config.getEnvironment());
                init.put(JobContext.UPLOAD_DIR, resolveUploadDir());
                return init;
            case "next":
                logger.info("next: {}", jm);
                JobChunk chunk = getNextChunk(jm.getExecutorId());
                if (chunk == null) {
                    logger.info("no more chunks, server responding with 'stop' message");
                    return new JobMessage("stop");
                }
                String uploadDir = jm.get(JobContext.UPLOAD_DIR, String.class);
                ScenarioRuntime sr = (ScenarioRuntime) chunk.getChunk();
                JobContext jc = new JobContext(sr.scenario, jobId, jm.getExecutorId(), chunk.getChunkId(), uploadDir);
                JobMessage next = new JobMessage("next")
                        .put("preCommands", config.getPreCommands(jc))
                        .put("mainCommands", config.getMainCommands(jc))
                        .put("postCommands", config.getPostCommands(jc));
                next.setChunkId(chunk.getChunkId());
                return next;
            case "upload":
                logger.info("upload: {}", jm);
                handleUpload(jm.getBytes(), jm.getExecutorId(), jm.getChunkId());
                JobMessage upload = new JobMessage("upload");
                upload.setChunkId(jm.getChunkId());
                return upload;
            default:
                logger.warn("unknown request method: {}", method);
                return null;
        }
    }

    public static File getFirstFileWithExtension(File parent, String extension) {
        File[] files = parent.listFiles((f, n) -> n.endsWith("." + extension));
        return files == null || files.length == 0 ? null : files[0];
    }

    public void startExecutors() {
        try {
            config.startExecutors(jobId, jobUrl);
        } catch (Exception e) {
            logger.error("failed to start executors: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String resolveUploadDir() {
        String temp = config.getUploadDir();
        if (temp != null) {
            return temp;
        }
        return reportDir;
    }

    private byte[] getDownload() {
        try {
            InputStream is = new FileInputStream(ZIP_FILE);
            return FileUtils.toBytes(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleUpload(byte[] bytes, String executorId, String chunkId) {
//        String chunkBasePath = basePath + File.separator + executorId + File.separator + chunkId;
//        File zipFile = new File(chunkBasePath + ".zip");
//        FileUtils.writeToFile(zipFile, bytes);
//        File upload = new File(chunkBasePath);
//        JobUtils.unzip(zipFile, upload);
        File upload = new File("");
        handleUpload(upload, executorId, chunkId);
    }

    protected void dumpLog(JobMessage jm) {
        logger.debug("\n>>>>>>>>>>>>>>>>>>>>> {}\n{}<<<<<<<<<<<<<<<<<<<< {}", jm, jm.get("log", String.class), jm);
    }

}
