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
import com.intuit.karate.JsonUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class DapDecoder extends ByteToMessageDecoder {

    private static final Logger logger = LoggerFactory.getLogger(DapDecoder.class);

    public static final String CRLFCRLF = "\r\n\r\n";

    private final StringBuilder buffer = new StringBuilder();
    private int remaining;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int readable = in.readableBytes();
        buffer.append(in.readCharSequence(readable, FileUtils.UTF8));
        if (remaining > 0 && buffer.length() >= remaining) {
            out.add(encode(buffer.substring(0, remaining)));
            String rhs = buffer.substring(remaining);
            buffer.setLength(0);
            buffer.append(rhs);
            remaining = 0;
        }
        int pos;
        while ((pos = buffer.indexOf(CRLFCRLF)) != -1) {
            String rhs = buffer.substring(pos + 4);
            int colonPos = buffer.lastIndexOf(":", pos);
            String lengthString = buffer.substring(colonPos + 1, pos);
            int length = Integer.valueOf(lengthString.trim());
            buffer.setLength(0);
            if (rhs.length() >= length) {
                String msg = rhs.substring(0, length);
                out.add(encode(msg));
                buffer.append(rhs.substring(length));
                remaining = 0;
            } else {
                remaining = length;
                buffer.append(rhs);
            }
        }
    }

    private static DapMessage encode(String raw) {
        logger.debug(">> {}", raw);
        Map<String, Object> map = JsonUtils.toJsonDoc(raw).read("$");
        return new DapMessage(map);
    }

}
