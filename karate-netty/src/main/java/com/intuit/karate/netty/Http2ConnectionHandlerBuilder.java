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

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

import static io.netty.handler.logging.LogLevel.INFO;

import com.intuit.karate.cucumber.FeatureProvider;

public final class Http2ConnectionHandlerBuilder
        extends AbstractHttp2ConnectionHandlerBuilder<FeatureServerHttp2ConnectionHandler, Http2ConnectionHandlerBuilder> {

    private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, FeatureServerHttp2ConnectionHandler.class);
    private final FeatureProvider provider;
    private final Runnable stopFunction;

    public Http2ConnectionHandlerBuilder(FeatureProvider provider, Runnable stopFunction) {
        frameLogger(logger);
        this.provider = provider;
        this.stopFunction = stopFunction;
    }

    @Override
    public FeatureServerHttp2ConnectionHandler build() {
        return super.build();
    }

    @Override
    protected FeatureServerHttp2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                           Http2Settings initialSettings) {
        FeatureServerHttp2ConnectionHandler handler = new FeatureServerHttp2ConnectionHandler(provider, stopFunction, decoder, encoder, initialSettings);
        InboundHttp2ToHttpAdapter listener = new InboundHttp2ToHttpAdapterBuilder(handler.connection())
        		.maxContentLength(1048576)
        		.validateHttpHeaders(false)
        		.propagateSettings(true)
        		.build();
        frameListener(listener);
        return handler;
    }
}
