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
package com.intuit.karate.job;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public abstract class JobServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    protected static final Logger logger = LoggerFactory.getLogger(JobServerHandler.class);
    protected final JobServer server;

    public JobServerHandler(JobServer server) {
        this.server = server;
    }

    private static DefaultFullHttpResponse response(String message) {
        ByteBuf responseBuf = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8);
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseBuf);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        FullHttpResponse response;
        if (!HttpMethod.POST.equals(msg.method())) {
            logger.warn("ignoring non-POST request: {}", msg);
            response = response(msg.method() + " not supported\n");
        } else {
            String method = StringUtils.trimToNull(msg.headers().get(JobMessage.KARATE_METHOD));
            String executorId = StringUtils.trimToNull(msg.headers().get(JobMessage.KARATE_EXECUTOR_ID));
            String chunkId = StringUtils.trimToNull(msg.headers().get(JobMessage.KARATE_CHUNK_ID));
            String contentType = StringUtils.trimToNull(msg.headers().get(HttpHeaderNames.CONTENT_TYPE));
            if (method == null) {
                response = response(JobMessage.KARATE_METHOD + " header is null\n");
            } else {
                HttpContent httpContent = (HttpContent) msg;
                ByteBuf content = httpContent.content();
                byte[] bytes;
                if (content.isReadable()) {
                    bytes = new byte[content.readableBytes()];
                    content.readBytes(bytes);
                } else {
                    bytes = null;
                }
                JobMessage req;
                if (contentType.contains("octet-stream")) {
                    if (chunkId == null) {
                        logger.warn("chunk id is null for binary upload from executor");
                    }
                    req = new JobMessage(method);
                    req.setBytes(bytes);
                } else {
                    String json = FileUtils.toString(bytes);
                    Map<String, Object> map = JsonUtils.toJsonDoc(json).read("$");
                    req = new JobMessage(method, map);
                }
                req.setExecutorId(executorId);
                req.setChunkId(chunkId);
                logger.debug("<< {}", req);
                JobMessage res = handle(req);
                logger.debug(">> {}", res);
                if (res == null) {
                    response = response("unable to create response for: " + req + "\n");
                } else {
                    bytes = res.getBytes();
                    boolean binary;
                    if (bytes == null) {
                        binary = false;
                        String json = JsonUtils.toJson(res.body);
                        bytes = FileUtils.toBytes(json);
                    } else {
                        binary = true;
                    }
                    ByteBuf responseBuf = Unpooled.copiedBuffer(bytes);
                    response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseBuf);
                    response.headers().add(JobMessage.KARATE_METHOD, res.method);
                    response.headers().add(JobMessage.KARATE_JOB_ID, server.jobId);
                    if (res.getExecutorId() != null) {
                        response.headers().add(JobMessage.KARATE_EXECUTOR_ID, res.getExecutorId());
                    }
                    if (res.getChunkId() != null) {
                        response.headers().add(JobMessage.KARATE_CHUNK_ID, res.getChunkId());
                    }
                    if (binary) {
                        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
                    } else {
                        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    }
                }
            }
        }
        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
    
    protected void dumpLog(JobMessage jm) {
        logger.debug("\n>>>>>>>>>>>>>>>>>>>>> {}\n{}<<<<<<<<<<<<<<<<<<<< {}", jm, jm.get("log", String.class), jm);
    }

    protected abstract JobMessage handle(JobMessage request);

}
