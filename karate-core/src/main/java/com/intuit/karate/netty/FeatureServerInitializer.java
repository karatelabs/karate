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

import com.intuit.karate.cucumber.FeatureProvider;
import com.intuit.karate.cucumber.FeatureWrapper;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureServerInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureServerInitializer.class);
    private static final int UPGRADE_REQ_LENGTH_MAX = 16384;
    
    private final SslContext sslCtx;
    private final FeatureBackend backend;
    private final Runnable stopFunction;
    
    private class FeatureServerUpgradeCodecFactory implements UpgradeCodecFactory {
        private FeatureProvider provider;
        private Runnable stopFunction;
    	
        public UpgradeCodecFactory featureInit(FeatureProvider provider, Runnable stopFunction) {
        	this.provider = provider;
        	this.stopFunction = stopFunction; 
			return this;
        }

		@Override
		public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
          	    return new Http2ServerUpgradeCodec(new Http2ConnectionHandlerBuilder(provider, stopFunction).build());
            } else {
                return null;
            }
        }
    }
    
    public FeatureServerInitializer(SslContext sslCtx, File featureFile, Map<String, Object> vars, Runnable stopFunction) {
        this.sslCtx = sslCtx;
        Feature feature = FeatureParser.parse(featureFile);
        backend = new FeatureBackend(feature, vars);
        this.stopFunction = stopFunction;
    }
    
    @Override
    public void initChannel(SocketChannel ch) {
    	
    	ChannelPipeline p = ch.pipeline();
    	if (sslCtx != null) {
        	setupForSSL(ch);
        }
        else {
        	setupForClearText(p);
        }
    }

	private void setupForClearText(ChannelPipeline p) {
		logger.info("server setup for ClearText");
//        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
//        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
//                .propagateSettings(true).validateHttpHeaders(true)
//                .maxContentLength(1048576).build();
//
//        p.addLast(new HttpToHttp2ConnectionHandlerBuilder()
//                .frameListener(listener)
//                // .frameLogger(TilesHttp2ToHttpHandler.logger)
//                .connection(connection).build());
		
        final HttpServerCodec sourceCodec = new HttpServerCodec();
        final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, new FeatureServerUpgradeCodecFactory().featureInit(provider, stopFunction), UPGRADE_REQ_LENGTH_MAX);
        final ChannelHandler featureServerConnectionHandler = new Http2ConnectionHandlerBuilder(provider, stopFunction).build();
        final CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, featureServerConnectionHandler);

        // First in the pipeline is the cleartext upgrade handler
        // Followed by a HTTP/2 request handler
        p.addLast(cleartextHttp2ServerUpgradeHandler);
        p.addLast(new FeatureServerHttp2RequestHandler(provider, stopFunction));
        // HTTP/1 requests fall-through to the HTTP/1 handler
        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new FeatureServerHandlerHttp1(provider, stopFunction));
	}

	private void setupForSSL(SocketChannel ch) {
		logger.info("server setup for SSL");
		ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()), new Http2OrHttpHandler(provider, stopFunction));
		
	}
    
}
