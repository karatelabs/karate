/*
 * The MIT License
 *
 * Copyright 2017 intuit Inc.
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
package com.intuit.karate.http.apache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

/**
 *
 * @author pthomas3
 */
public class LoggingEntityWrapper extends HttpEntityWrapper {
    
    private final byte[] bytes;
    
    public LoggingEntityWrapper(HttpEntity wrappedEntity) {
        super(wrappedEntity);
        try {
            ByteArrayOutputStream temp = new ByteArrayOutputStream();
            wrappedEntity.writeTo(temp);
            bytes = temp.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes() {
        return bytes;
    }        

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public InputStream getContent() throws IOException {
        return new ByteArrayInputStream(bytes);
    }   

    @Override
    public long getContentLength() {
        return bytes.length;
    }        

    @Override
    public void writeTo(OutputStream out) throws IOException {
        out.write(bytes);
    } 

    @Override
    public boolean isStreaming() {
        return false;
    }        
    
}
