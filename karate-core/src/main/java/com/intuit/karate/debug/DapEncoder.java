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
package com.intuit.karate.debug;

import com.intuit.karate.FileUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DapEncoder extends MessageToMessageEncoder<DapMessage> {

    private static final Logger logger = LoggerFactory.getLogger(DapEncoder.class);

    private static final byte[] CONTENT_LENGTH_COLON = "Content-Length: ".getBytes(FileUtils.UTF8);
    private static final byte[] CRLFCRLF = "\r\n\r\n".getBytes(FileUtils.UTF8);

    @Override
    protected void encode(ChannelHandlerContext ctx, DapMessage dm, List<Object> out) throws Exception {
        String msg = dm.toJson();
        if (logger.isTraceEnabled()) {
            logger.trace("<< {}", msg);
        }
        ByteBuf buf = ctx.alloc().buffer();
        byte[] bytes = msg.getBytes(FileUtils.UTF8);
        buf.writeBytes(CONTENT_LENGTH_COLON);
        buf.writeCharSequence(bytes.length + "", FileUtils.UTF8);
        buf.writeBytes(CRLFCRLF);
        buf.writeBytes(bytes);
        out.add(buf);
    }

}
