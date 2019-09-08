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
import com.intuit.karate.core.ScenarioExecutionUnit;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JobServer {

    private static final Logger logger = LoggerFactory.getLogger(JobServer.class);

    protected final JobConfig config;
    protected final List<FeatureUnits> FEATURE_UNITS = new ArrayList();
    protected final Map<String, ScenarioExecutionUnit> CHUNKS = new HashMap();
    protected final String basePath;
    protected final File ZIP_FILE;
    protected final String jobId;
    protected final String jobUrl;
    protected final String reportDir;
    protected final AtomicInteger executorCount = new AtomicInteger(1);

    private final Channel channel;
    private final int port;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;    

    public void startExecutors() {
        config.startExecutors(jobId, jobUrl);
    }

    protected String resolveReportPath() {
        String reportPath = config.getReportPath();
        if (reportPath != null) {
            return reportPath;
        }
        return reportDir;
    }

    public void addFeatureUnits(List<ScenarioExecutionUnit> units, Runnable onDone) {
        synchronized (FEATURE_UNITS) {
            FEATURE_UNITS.add(new FeatureUnits(units, onDone));
        }
    }

    public ScenarioExecutionUnit getNextChunk() {
        synchronized (FEATURE_UNITS) {
            if (FEATURE_UNITS.isEmpty()) {
                return null;
            } else {
                FeatureUnits job = FEATURE_UNITS.get(0);
                ScenarioExecutionUnit unit = job.units.remove(0);
                if (job.units.isEmpty()) {
                    job.onDone.run();
                    FEATURE_UNITS.remove(0);
                }
                return unit;
            }
        }
    }

    public String addChunk(ScenarioExecutionUnit unit) {
        synchronized (CHUNKS) {
            String chunkId = (CHUNKS.size() + 1) + "";
            CHUNKS.put(chunkId, unit);
            return chunkId;
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
    
    public void saveChunkOutput(byte[] bytes, String executorId, String chunkId) {
        String chunkBasePath = basePath + File.separator + executorId + File.separator + chunkId;
        File zipFile = new File(chunkBasePath + ".zip");
        FileUtils.writeToFile(zipFile, bytes);
        JobUtils.unzip(zipFile, new File(chunkBasePath));
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
        logger.info("stop: shutting down");
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        logger.info("stop: shutdown complete");
    }

    public JobServer(JobConfig config, String reportDir) {
        this.config = config;
        this.reportDir = reportDir;
        jobId = System.currentTimeMillis() + "";
        basePath = FileUtils.getBuildDir() + File.separator + jobId;        
        ZIP_FILE = new File(basePath + ".zip");
        JobUtils.zip(new File(config.getSourcePath()), ZIP_FILE);
        logger.info("created zip archive: {}", ZIP_FILE);
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
            logger.info("job server started - {} - {}", jobUrl, jobId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
