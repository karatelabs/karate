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
import com.intuit.karate.core.ExecutionContext;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class JobServer {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(JobServer.class);

    protected final JobConfig config;
    protected final String basePath;
    protected final File ZIP_FILE;
    protected final String jobId;
    protected final String jobUrl;
    protected final String reportDir;
    protected final AtomicInteger executorCounter = new AtomicInteger(1);

    private final Channel channel;
    private final int port;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    
    public static File getFirstFileWithExtension(File parent, String extension) {
        File[] files = parent.listFiles((f, n) -> n.endsWith("." + extension));
        return files.length == 0 ? null : files[0];
    }    

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
    
    public byte[] getDownload() {
        try {
            InputStream is = new FileInputStream(ZIP_FILE);
            return FileUtils.toBytes(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    

    public abstract void addFeature(ExecutionContext exec, List<ScenarioExecutionUnit> units, Runnable onComplete);

    public abstract ChunkResult getNextChunk(String executorId);

    public abstract void handleUpload(File file, String executorId, String chunkId);
    
    protected void handleUpload(byte[] bytes, String executorId, String chunkId) {
        String chunkBasePath = basePath + File.separator + executorId + File.separator + chunkId;
        File zipFile = new File(chunkBasePath + ".zip");
        FileUtils.writeToFile(zipFile, bytes);
        File upload = new File(chunkBasePath);
        JobUtils.unzip(zipFile, upload);
        handleUpload(upload, executorId, chunkId);
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
                            // just to make header size more than the default
                            p.addLast(new HttpServerCodec(4096, 12288, 8192));
                            p.addLast(new HttpObjectAggregator(1048576));
                            p.addLast(new ScenarioJobServerHandler(JobServer.this));
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
