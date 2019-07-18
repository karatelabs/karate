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

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureBackend;
import com.intuit.karate.core.FeatureParser;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.InputStream;
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

    public static FeatureServer start(File featureFile, int port, boolean ssl, Map<String, Object> arg) {
        return new FeatureServer(featureFile, port, ssl, arg);
    }

    public static FeatureServer start(File featureFile, int port, File certFile, File privateKeyFile, Map<String, Object> arg) {
        return new FeatureServer(featureFile, port, certFile, privateKeyFile, arg);
    }

    private final Channel channel;
    private final String host;
    private final int port;
    private final boolean ssl;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final FeatureBackend backend;

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

    private static SslContext getSslContext() { // self signed
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SslContext getSslContext(File certFile, File keyFile) {
        try {
            return SslContextBuilder.forServer(certFile, keyFile).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SslContext getSslContext(InputStream certStream, InputStream keyStream) {
        try {
            return SslContextBuilder.forServer(certStream, keyStream).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public FeatureServer(Feature feature, int port, InputStream certStream, InputStream keyStream, Map<String, Object> arg) {
        this(feature, port, getSslContext(certStream, keyStream), arg);
    }

    private FeatureServer(File file, int port, File certificate, File privateKey, Map<String, Object> arg) {
        this(toFeature(file), port, getSslContext(certificate, privateKey), arg);
    }

    public FeatureServer(Feature feature, int port, boolean ssl, Map<String, Object> arg) {
        this(feature, port, ssl ? getSslContext() : null, arg);
    }

    private FeatureServer(File file, int port, boolean ssl, Map<String, Object> arg) {
        this(toFeature(file), port, ssl ? getSslContext() : null, arg);
    }

    private static Feature toFeature(File file) {
        File parent = file.getParentFile();
        if (parent == null) { // when running via command line and same dir
            file = new File(file.getAbsolutePath());
        }
        return FeatureParser.parse(file);
    }

    private FeatureServer(Feature feature, int requestedPort, SslContext sslCtx, Map<String, Object> arg) {
        ssl = sslCtx != null;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        backend = new FeatureBackend(feature, arg);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(getClass().getName(), LogLevel.TRACE))
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel c) {
                            ChannelPipeline p = c.pipeline();
                            if (ssl) {
                                p.addLast(sslCtx.newHandler(c.alloc()));
                            }
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(1048576));
                            p.addLast(new FeatureServerHandler(backend, ssl, () -> stop()));
                        }
                    });
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
