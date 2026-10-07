/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.http;


import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sits right after the codec, so a message the decoder failed on is answered before aggregation
 * (which would 413 on its Content-Length) or CORS (which would answer a preflight) can act on headers
 * cut short at the failure. The decoder discards the rest of the connection, hence the close.
 */
class DecoderFailureHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DecoderFailureHandler.class);

    private boolean rejected;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (rejected) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (!(msg instanceof HttpObject httpObject) || !httpObject.decoderResult().isFailure()) {
            ctx.fireChannelRead(msg);
            return;
        }
        rejected = true;
        Throwable cause = httpObject.decoderResult().cause();
        ReferenceCountUtil.release(msg);
        HttpResponseStatus status = cause instanceof TooLongHttpHeaderException
                ? HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE
                : cause instanceof TooLongHttpLineException
                ? HttpResponseStatus.REQUEST_URI_TOO_LONG
                : HttpResponseStatus.BAD_REQUEST;
        logger.warn("bad request: {}", cause.getMessage());
        // the cause can quote the request (an invalid header name), so the body stays fixed
        ctx.writeAndFlush(HttpServerHandler.error(status, status.reasonPhrase()))
                .addListener(ChannelFutureListener.CLOSE);
    }

}
