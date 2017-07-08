/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.http;

import java.io.InputStream;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author pthomas3
 */
public class HttpBody {
    
    private final byte[] bytes;
    private final InputStream stream;
    private final String contentType;
    
    public boolean isStream() {
        return stream != null;
    }

    public byte[] getBytes() {
        if (isStream()) {
            try {
                return IOUtils.toByteArray(stream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return bytes;
    }

    public InputStream getStream() {
        return stream;
    }

    public String getContentType() {
        return contentType;
    }        
    
    private HttpBody(byte[] bytes, InputStream stream, String contentType) {
        this.bytes = bytes;
        this.stream = stream;
        this.contentType = contentType;
    }
    
    public static HttpBody string(String value, String contentType) {
        return new HttpBody(value.getBytes(), null, contentType);
    }
    
    public static HttpBody stream(InputStream stream, String contentType) {
        return new HttpBody(null, stream, contentType);
    }
    
    public static HttpBody bytes(byte[] bytes, String contentType) {
        return new HttpBody(bytes, null, contentType);
    }
    
}
