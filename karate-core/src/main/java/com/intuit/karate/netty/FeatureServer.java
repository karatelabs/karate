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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureServer {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServer.class);

    public static FeatureServer start(File featureFile, int port, boolean ssl, Map<String, Object> vars) {
        return new FeatureServer(featureFile, port, ssl, vars);
    }
    
    public static FeatureServer start(File featureFile, int port, File certFile, File privateKeyFile, Map<String, Object> vars) {
        return new FeatureServer(featureFile, port, certFile, privateKeyFile, vars);
    }    

    private final Channel channel;
    private final String host;
    private final int port;
    private final boolean ssl;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

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

    private static SslContext getSelfSignedSslContext() {
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static SslContext getSslContextFromFiles(File sslCert, File sslPrivateKey) {
        try {
            return SslContextBuilder.forServer(sslCert, sslPrivateKey).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FeatureServer(File featureFile, int port, File certificate, File privateKey, Map<String, Object> vars) {
        this(featureFile, port, getSslContextFromFiles(certificate, privateKey), vars);
    }

    private FeatureServer(File featureFile, int port, boolean ssl, Map<String, Object> vars) {
        this(featureFile, port, ssl ? getSelfSignedSslContext() : null, vars);
    }

    private FeatureServer(File featureFile, int requestedPort, SslContext sslCtx, Map<String, Object> vars) {
        ssl = sslCtx != null;
        File parent = featureFile.getParentFile();
        if (parent == null) { // when running via command line and same dir
            featureFile = new File(featureFile.getAbsolutePath());
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        FeatureServerInitializer initializer = new FeatureServerInitializer(sslCtx, featureFile, vars, () -> stop());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(getClass().getName(), LogLevel.TRACE))
                    .childHandler(initializer);
            channel = b.bind(requestedPort).sync().channel();
            InetSocketAddress isa = (InetSocketAddress) channel.localAddress();
            host = "127.0.0.1"; //isa.getHostString();
            port = isa.getPort();
            logger.info("server started - {}://{}:{}", ssl ? "https" : "http", host, port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
