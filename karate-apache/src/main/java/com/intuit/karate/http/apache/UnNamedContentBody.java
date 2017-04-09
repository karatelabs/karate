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
package com.intuit.karate.http.apache;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.http.entity.mime.content.ContentBody;

/**
 *
 * @author pthomas3
 */
public class UnNamedContentBody implements ContentBody {
    
    private String content;
    
    public UnNamedContentBody(String content) {
        this.content = content;
    }

    @Override
    public String getFilename() {
        return null;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public String getMediaType() {
        return null;
    }

    @Override
    public String getSubType() {
        return null;
    }

    @Override
    public String getCharset() {
        return null;
    }

    @Override
    public String getTransferEncoding() {
        return null;
    }

    @Override
    public long getContentLength() {
        return -1;
    }
    
}
