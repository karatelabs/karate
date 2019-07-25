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
package com.intuit.karate.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyRemoteHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private static final Logger logger = LoggerFactory.getLogger(ProxyRemoteHandler.class);

    private final ProxyContext proxyContext;
    private final ProxyClientHandler clientHandler;
    private final RequestFilter requestFilter;
    private final ResponseFilter responseFilter;
    private final Channel clientChannel;
    private final FullHttpRequest initialRequest;

    protected Channel remoteChannel;
    protected FullHttpRequest currentRequest;

    public ProxyRemoteHandler(ProxyContext proxyContext, ProxyClientHandler clientHandler, FullHttpRequest initialRequest) {
        this.proxyContext = proxyContext;
        this.clientHandler = clientHandler;
        this.clientChannel = clientHandler.clientChannel;
        this.requestFilter = clientHandler.requestFilter;
        this.responseFilter = clientHandler.responseFilter;
        this.initialRequest = initialRequest;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.debug("<< {}", response);
        }
        ProxyResponse filtered = responseFilter == null ? null : responseFilter.apply(proxyContext, currentRequest, response);
        if (filtered == null || filtered.response == null) {
            ReferenceCountUtil.retain(response);
        } else {
            response = filtered.response;
            if (logger.isTraceEnabled()) {
                logger.debug("<<<< {}", response);
            }            
        }        
        clientChannel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void send(FullHttpRequest request) {
        currentRequest = request;
        FullHttpRequest filtered;
        if (requestFilter != null) {
            ProxyResponse pr = requestFilter.apply(proxyContext, request);
            if (pr != null && pr.response != null) { // short circuit
                clientChannel.writeAndFlush(pr.response);
                return;
            }
            filtered = pr == null ? null : pr.request; // if not null, is transformed
        } else {
            filtered = null;
        }
        if (logger.isTraceEnabled()) {
            logger.trace(">> before: {}", request);
        }
        if (filtered == null) {
            ReferenceCountUtil.retain(request);
            filtered = request;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace(">>>> after: {}", filtered);
            }
        }
        NettyUtils.fixHeadersForProxy(filtered);
        remoteChannel.writeAndFlush(filtered);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        remoteChannel = ctx.channel();
        if (initialRequest != null) { // only if not ssl
            send(initialRequest);
            clientHandler.unlockAndProceed();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause.getMessage() == null) {
            cause.printStackTrace();
        } else {
            logger.error("closing proxy outbound connection: {}", cause.getMessage());
        }
        ctx.close();
        NettyUtils.flushAndClose(clientChannel);
    }

    @Override
    public String toString() {
        return remoteChannel + "";
    }

}
