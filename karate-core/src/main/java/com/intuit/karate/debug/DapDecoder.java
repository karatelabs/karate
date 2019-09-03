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
import io.netty.util.ByteProcessor;
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
    
    private int remaining;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (remaining > 0 && in.readableBytes() >= remaining) {
            out.add(encode(in, remaining));
            remaining = 0;
        }
        int pos;
        while ((pos = findCrLfCrLf(in)) != -1) {
            int delimiterPos = pos;
            while (in.getByte(--pos) != ':') {
                // skip backwards
            }
            in.readerIndex(++pos);
            CharSequence lengthString = in.readCharSequence(delimiterPos - pos, FileUtils.UTF8);
            int length = Integer.valueOf(lengthString.toString().trim());
            in.readerIndex(delimiterPos + 4);
            if (in.readableBytes() >= length) {
                out.add(encode(in, length));
                remaining = 0;
            } else {
                remaining = length;
            }
        }
    }
    
    private static int findCrLfCrLf(ByteBuf buffer) {
        int totalLength = buffer.readableBytes();
        int readerIndex = buffer.readerIndex();
        int i = buffer.forEachByte(readerIndex, totalLength, ByteProcessor.FIND_LF);
        if (i > 0 && buffer.getByte(i - 1) == '\r') {
            int more = readerIndex + totalLength - i;
            if (more > 1 && buffer.getByte(i + 1) == '\r' && buffer.getByte(i + 2) == '\n') {
                return i - 1;
            }
        }
        return -1;
    }    

    private static DapMessage encode(ByteBuf in, int length) {
        String msg = in.readCharSequence(length, FileUtils.UTF8).toString();
        if (logger.isTraceEnabled()) {
            logger.trace(">> {}", msg);
        }
        Map<String, Object> map = JsonUtils.toJsonDoc(msg).read("$");
        return new DapMessage(map);
    }

}
