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

/**
 *
 * @author pthomas3
 */
public class ScenarioJobServerHandler extends JobServerHandler {

    public ScenarioJobServerHandler(JobServer server) {
        super(server);
    }

    @Override
    protected JobMessage handle(JobMessage jm) {
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
                download.setBytes(server.getDownload());
                int executorId = server.executorCounter.getAndIncrement();
                download.setExecutorId(executorId + "");
                return download;
            case "init":
                logger.info("init: {}", jm);
                JobMessage init = new JobMessage("init");
                init.put("startupCommands", server.config.getStartupCommands());
                init.put("shutdownCommands", server.config.getShutdownCommands());
                init.put("environment", server.config.getEnvironment());
                init.put(JobContext.UPLOAD_DIR, server.resolveUploadDir());
                return init;
            case "next":
                logger.info("next: {}", jm);
                ChunkResult chunk = server.getNextChunk(jm.getExecutorId());
                if (chunk == null) {
                    logger.info("no more chunks, server responding with 'stop' message");
                    return new JobMessage("stop");
                }
                String uploadDir = jm.get(JobContext.UPLOAD_DIR, String.class);
                JobContext jc = new JobContext(chunk.scenario, server.jobId, jm.getExecutorId(), chunk.getChunkId(), uploadDir);
                JobMessage next = new JobMessage("next")
                        .put("preCommands", server.config.getPreCommands(jc))
                        .put("mainCommands", server.config.getMainCommands(jc))
                        .put("postCommands", server.config.getPostCommands(jc));
                next.setChunkId(chunk.getChunkId());
                return next;
            case "upload":
                logger.info("upload: {}", jm);
                server.handleUpload(jm.getBytes(), jm.getExecutorId(), jm.getChunkId());
                return new JobMessage("upload");
            default:
                logger.warn("unknown request method: {}", method);
                return null;
        }
    }

}
