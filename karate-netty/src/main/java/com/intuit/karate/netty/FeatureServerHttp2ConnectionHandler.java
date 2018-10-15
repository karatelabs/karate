/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.intuit.karate.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.ReferenceCountUtil;

/**
 * Request handler that serves HTTP/2 responses according to the specified Karate feature
 */
public final class FeatureServerHttp2ConnectionHandler extends Http2ConnectionHandler {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServerHttp2ConnectionHandler.class);

    FeatureServerHttp2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, 
    		Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

	@Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
   		logger.info("ConnectionHandler channelReadComplete: ) ");
        ctx.flush();
        super.channelReadComplete(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logger.debug("ConnectionHandler write: ({}) ", msg);
    	if (msg instanceof FullHttpResponse) {
    		FullHttpResponse response = (FullHttpResponse)msg;
    		Http2Headers headers = HttpConversionUtil.toHttp2Headers(response, false);
            int streamId = getStreamId(response);
            encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
            encoder().writeData(ctx, streamId, response.content(), 0, true, ctx.newPromise());
    		
            return;
    	}
    	
    	ctx.write(msg, promise);
    }

    /**
     * @param response
     * @return
     */
    private int getStreamId(FullHttpResponse response) {
        int streamId = response.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 1);
        return streamId;
    }

	
    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, pushes the embedded, 
     * aggregated HTTP request to the feature server request handler).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.info("ConnectionHandler userEventTriggered: ({}) ", evt);
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            HttpServerUpgradeHandler.UpgradeEvent upgradeEvent =
                    (HttpServerUpgradeHandler.UpgradeEvent) evt;
            ctx.fireChannelRead(ReferenceCountUtil.retain(upgradeEvent.upgradeRequest()));
        }

        super.userEventTriggered(ctx, evt);
    }

	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("ConnectionHandler exceptionCaught: ({}) ", cause);
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }
}
