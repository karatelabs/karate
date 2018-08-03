/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.intuit.karate.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intuit.karate.cucumber.FeatureProvider;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

/**
 * Used during protocol negotiation, the main function of this handler is to
 * return the HTTP/1.1 or HTTP/2 handler once the protocol has been negotiated.
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

    private static final Logger logger = LoggerFactory.getLogger(Http2OrHttpHandler.class);
    private static final int MAX_CONTENT_LENGTH = 1024 * 100;
    private final FeatureProvider provider;
    private final Runnable stopFunction;

    protected Http2OrHttpHandler(FeatureProvider provider, Runnable stopFunction) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.provider = provider;
        this.stopFunction = stopFunction;        
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    	logger.info("userEventTriggered: {} ", evt);
        if (evt instanceof SslHandshakeCompletionEvent) {

        	SslHandshakeCompletionEvent handshakeEvent = (SslHandshakeCompletionEvent) evt;
        	logger.info("userEventTriggered: {} ", handshakeEvent);
            if (handshakeEvent.isSuccess()) {
            	logger.info("userEventTriggered: Success ");
            	SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                if (sslHandler != null) {
                	logger.info("userEventTriggered: sslHandler{} ", sslHandler);
                }
                String protocol = sslHandler.applicationProtocol();
                logger.info("userEventTriggered: protocol = {} ", protocol);
            } else {
            	logger.info("userEventTriggered: Failed " );
            }
        }

        super.userEventTriggered(ctx, evt);
    }
    
    
    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
	    logger.info("configurePipeline: {} ", protocol);
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            configureHttp2(ctx);
            return;
        }

        if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            configureHttp1(ctx);
            return;
        }

        throw new IllegalStateException("unknown protocol: " + protocol);
    }

    private void configureHttp2(ChannelHandlerContext ctx) {
	    logger.info("configureHttp2");
	    
        DefaultHttp2Connection connection = new DefaultHttp2Connection(true);
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(connection)
                .propagateSettings(true).validateHttpHeaders(false)
                .maxContentLength(MAX_CONTENT_LENGTH).build();

        ctx.pipeline().addLast(new HttpToHttp2ConnectionHandlerBuilder()
                .frameListener(listener)
                .connection(connection).build());

        ctx.pipeline().addLast(new FeatureServerHttp2RequestHandler(provider, stopFunction));
        //ctx.pipeline().addLast(new FeatureServerHttp2Handler2(provider, stopFunction));
    }

    private void configureHttp1(ChannelHandlerContext ctx) throws Exception {
	    logger.info("configureHttp1");
        ctx.pipeline().addLast(new HttpServerCodec(),
                               new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                               new FeatureServerHandlerHttp1(provider, stopFunction));
    }
}
