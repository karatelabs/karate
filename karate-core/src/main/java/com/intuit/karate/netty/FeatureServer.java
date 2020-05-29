/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.netty;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeaturesBackend;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pthomas3
 */
public class FeatureServer {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServer.class);

    public static final String DEFAULT_CERT_NAME = "cert.pem";
    public static final String DEFAULT_KEY_NAME = "key.pem";

    public static FeatureServer start(File featureFile, int port, boolean ssl, Map<String, Object> arg) {
        return start(Collections.singletonList(featureFile), port, ssl, arg);
    }

    public static FeatureServer start(List<File> featureFiles, int port, boolean ssl, Map<String, Object> arg) {
        return new FeatureServer(featureFiles.toArray(new File[0]), port, ssl, arg);
    }

    public static FeatureServer start(File featureFile, int port, boolean ssl, File certFile, File privateKeyFile, Map<String, Object> arg) {
        return start(Collections.singletonList(featureFile), port, ssl, certFile, privateKeyFile, arg);
    }

    public static FeatureServer start(List<File> featureFiles, int port, boolean ssl, File certFile, File privateKeyFile, Map<String, Object> arg) {
        return new FeatureServer(featureFiles.toArray(new File[0]), port, ssl, certFile, privateKeyFile, arg);
    }

    private final Channel channel;
    private final String host;
    private final int port;
    private final boolean ssl;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final FeaturesBackend backend;
    
    public String getBaseUrl() {
        return (ssl ? "https" : "http") + "://" + host + ":" + port;
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

    private static Supplier<SslContext> getContextSupplier(File certFileProvided, File keyFileProvided) {
        return () -> {
            try {
                File certFile = certFileProvided;
                File keyFile = keyFileProvided;
                if (certFile == null || keyFile == null) {
                    // attempt to re-use as far as possible
                    String buildDir = FileUtils.getBuildDir();
                    certFile = new File(buildDir + File.separator + DEFAULT_CERT_NAME);
                    keyFile = new File(buildDir + File.separator + DEFAULT_KEY_NAME);
                }
                if (!certFile.exists() || !keyFile.exists()) {
                    logger.warn("ssl - " + certFile + " and / or " + keyFile + " not found, will create");
                    NettyUtils.createSelfSignedCertificate(certFile, keyFile);
                } else {
                    logger.info("ssl - re-using existing files: {} and {}", certFile, keyFile);
                }
                return SslContextBuilder.forServer(certFile, keyFile).build();
            } catch (Exception e) {
                logger.warn("failed to create ssl context, will attempt throwaway creation: {}", e.getMessage());
                try {
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
                } catch (Exception ee) {
                    throw new RuntimeException(ee);
                }
            }
        };
    }

    private static Supplier<SslContext> getContextSupplierFromStreams(InputStream certStream, InputStream keyStream) {
        return () -> {
            try {
                return SslContextBuilder.forServer(certStream, keyStream).build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public FeatureServer(Feature feature, int port, boolean ssl, InputStream certStream, InputStream keyStream, Map<String, Object> arg) {
        this(new Feature[]{feature}, port, ssl, certStream, keyStream, arg);
    }

    public FeatureServer(Feature[] features, int port, boolean ssl, InputStream certStream, InputStream keyStream, Map<String, Object> arg) {
        this(features, port, ssl, getContextSupplierFromStreams(certStream, keyStream), arg);
    }

    private FeatureServer(File[] files, int port, boolean ssl, File certificate, File privateKey, Map<String, Object> arg) {
        this(toFeatures(files), port, ssl, getContextSupplier(certificate, privateKey), arg);
    }

    public FeatureServer(Feature feature, int port, boolean ssl, Map<String, Object> arg) {
        this(new Feature[]{feature}, port, ssl, arg);
    }

    public FeatureServer(Feature[] features, int port, boolean ssl, Map<String, Object> arg) {
        this(features, port, ssl, getContextSupplier(null, null), arg);
    }

    private FeatureServer(File[] files, int port, boolean ssl, Map<String, Object> arg) {
        this(toFeatures(files), port, ssl, getContextSupplier(null, null), arg);
    }

    private static Feature[] toFeatures(File[] files) {

        return Arrays.stream(files).map((f) -> toFeature(f)).toArray(Feature[]::new);
    }

    private static Feature toFeature(File file) {
        File parent = file.getParentFile();
        if (parent == null) { // when running via command line and same dir
            file = new File(file.getAbsolutePath());
        }
        return FeatureParser.parse(file);
    }

    private FeatureServer(Feature[] features, int requestedPort, boolean ssl, Supplier<SslContext> contextSupplier, Map<String, Object> arg) {
        this.ssl = ssl;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        backend = new FeaturesBackend(features, arg);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // .handler(new LoggingHandler(getClass().getName(), LogLevel.TRACE))
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel c) {
                            ChannelPipeline p = c.pipeline();
                            if (ssl) {
                                p.addLast(contextSupplier.get().newHandler(c.alloc()));
                            }
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(1048576));
                            p.addLast(new FeatureServerHandler(backend, ssl, contextSupplier, () -> stop()));
                        }
                    });
            channel = b.bind(requestedPort).sync().channel();
            InetSocketAddress isa = (InetSocketAddress) channel.localAddress();
            host = "127.0.0.1"; //isa.getHostString();
            port = isa.getPort();
            logger.info("server started - {}", getBaseUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
