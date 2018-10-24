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
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

/**
 * Used during protocol negotiation, the main function of this handler is to
 * return the HTTP/1.1 or HTTP/2 handler once the protocol has been negotiated.
 */
public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

    private static final Logger logger = LoggerFactory.getLogger(Http2OrHttpHandler.class);
    private final FeatureProvider provider;
    private final Runnable stopFunction;

    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;
    
    protected Http2OrHttpHandler(FeatureProvider provider, Runnable stopFunction) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.provider = provider;
        this.stopFunction = stopFunction;        
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
        
        ctx.pipeline().addLast(new Http2ConnectionHandlerBuilder().build());
        ctx.pipeline().addLast(new FeatureServerRequestHandler(provider, stopFunction));
    }

    private void configureHttp1(ChannelHandlerContext ctx) throws Exception {
        logger.info("configureHttp1");
        ctx.pipeline().addLast(new HttpServerCodec(),
                               new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                               new FeatureServerRequestHandler(provider, stopFunction));
    }
}
