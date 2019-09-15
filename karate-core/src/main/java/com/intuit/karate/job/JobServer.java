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
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Embed;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.ScenarioResult;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JobServer {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JobServer.class);

    protected final JobConfig config;
    protected final List<FeatureScenarios> FEATURE_QUEUE = new ArrayList();
    protected final Map<String, ChunkResult> CHUNK_RESULTS = new HashMap();
    protected final String basePath;
    protected final File ZIP_FILE;
    protected final String jobId;
    protected final String jobUrl;
    protected final String reportDir;
    protected final AtomicInteger executorCount = new AtomicInteger(1);
    private final AtomicInteger chunkCount = new AtomicInteger();

    private final Channel channel;
    private final int port;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public void startExecutors() {
        try {
            config.startExecutors(jobId, jobUrl);
        } catch (Exception e) {
            LOGGER.error("failed to start executors: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    protected String resolveUploadDir() {
        String temp = config.getUploadDir();
        if (temp != null) {
            return temp;
        }
        return this.reportDir;
    }

    public void addFeature(ExecutionContext exec, List<ScenarioExecutionUnit> units, Runnable onComplete) {
        Logger logger = new Logger();
        List<Scenario> selected = new ArrayList(units.size());
        for (ScenarioExecutionUnit unit : units) {
            if (FeatureExecutionUnit.isSelected(exec.featureContext, unit.scenario, logger)) {
                selected.add(unit.scenario);
            }
        }
        if (selected.isEmpty()) {
            onComplete.run();
        } else {
            FeatureScenarios fs = new FeatureScenarios(exec, selected, onComplete);
            FEATURE_QUEUE.add(fs);
        }
    }

    public ChunkResult getNextChunk() {
        synchronized (FEATURE_QUEUE) {
            if (FEATURE_QUEUE.isEmpty()) {
                return null;
            } else {
                FeatureScenarios feature = FEATURE_QUEUE.get(0);
                Scenario scenario = feature.scenarios.remove(0);
                if (feature.scenarios.isEmpty()) {
                    FEATURE_QUEUE.remove(0);
                }
                LOGGER.info("features queued: {}", FEATURE_QUEUE);
                ChunkResult chunk = new ChunkResult(feature, scenario);
                String chunkId = chunkCount.incrementAndGet() + "";
                chunk.setChunkId(chunkId);
                chunk.setStartTime(System.currentTimeMillis());
                feature.chunks.add(chunk);
                CHUNK_RESULTS.put(chunkId, chunk);
                return chunk;
            }
        }
    }

    public byte[] getZipBytes() {
        try {
            InputStream is = new FileInputStream(ZIP_FILE);
            return FileUtils.toBytes(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File getFirstFileWithExtension(File parent, String extension) {
        File[] files = parent.listFiles((f, n) -> n.endsWith("." + extension));
        return files.length == 0 ? null : files[0];
    }

    public void saveChunkOutput(byte[] bytes, String executorId, String chunkId) {
        String chunkBasePath = basePath + File.separator + executorId + File.separator + chunkId;
        File zipFile = new File(chunkBasePath + ".zip");
        FileUtils.writeToFile(zipFile, bytes);
        File outFile = new File(chunkBasePath);
        JobUtils.unzip(zipFile, outFile);
        File jsonFile = getFirstFileWithExtension(outFile, "json");
        if (jsonFile == null) {
            return;
        }
        String json = FileUtils.toString(jsonFile);
        File videoFile = getFirstFileWithExtension(outFile, "mp4");
        List<Map<String, Object>> list = JsonUtils.toJsonDoc(json).read("$[0].elements");
        synchronized (CHUNK_RESULTS) {
            ChunkResult cr = CHUNK_RESULTS.remove(chunkId);
            LOGGER.info("chunk complete: {}, remaining: {}", chunkId, CHUNK_RESULTS.keySet());
            if (cr == null) {
                LOGGER.error("could not find chunk: {}", chunkId);
                return;
            }
            ScenarioResult sr = new ScenarioResult(cr.scenario, list, true);
            sr.setStartTime(cr.getStartTime());
            sr.setEndTime(System.currentTimeMillis());
            sr.setThreadName(executorId);
            cr.setResult(sr);
            if (videoFile != null) {
                File dest = new File(FileUtils.getBuildDir()
                        + File.separator + "cucumber-html-reports" + File.separator + chunkId + ".mp4");
                FileUtils.copy(videoFile, dest);
                sr.appendEmbed(Embed.forVideoFile(dest.getName()));
            }
            if (cr.parent.scenarios.isEmpty()) {
                cr.parent.onComplete();
            }
        }
    }

    public int getPort() {
        return port;
    }

    public void waitSync() {
        try {
            channel.closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        LOGGER.info("stop: shutting down");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        LOGGER.info("stop: shutdown complete");
    }

    public JobServer(JobConfig config, String reportDir) {
        this.config = config;
        this.reportDir = reportDir;
        jobId = System.currentTimeMillis() + "";
        basePath = FileUtils.getBuildDir() + File.separator + jobId;
        ZIP_FILE = new File(basePath + ".zip");
        JobUtils.zip(new File(config.getSourcePath()), ZIP_FILE);
        LOGGER.info("created zip archive: {}", ZIP_FILE);
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // .handler(new LoggingHandler(getClass().getName(), LogLevel.TRACE))
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel c) {
                            ChannelPipeline p = c.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(1048576));
                            p.addLast(new JobServerHandler(JobServer.this));
                        }
                    });
            channel = b.bind(config.getPort()).sync().channel();
            InetSocketAddress isa = (InetSocketAddress) channel.localAddress();
            port = isa.getPort();
            jobUrl = "http://" + config.getHost() + ":" + port;
            LOGGER.info("job server started - {} - {}", jobUrl, jobId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
