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
package com.intuit.karate.mock.servlet;

import static com.intuit.karate.ScriptValue.Type;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.HttpUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Part;

/**
 *
 * @author pthomas3
 */
public class MockMultiPart implements Part {
    
    private final MultiPartItem item;
    private final byte[] bytes;
    private final Map<String, String> headers;
    private static final String CONTENT_TYPE = "content-type";
    private static final String CONTENT_DISPOSITION = "content-disposition";        
    
    public MockMultiPart(MultiPartItem item) {
        this.item = item;
        if (item.getValue().isNull()) {
            bytes = new byte[0];
        } else {
            String temp = item.getValue().getAsString();
            bytes = temp.getBytes();
        }
        headers = new HashMap<>(2);
        headers.put(CONTENT_TYPE, HttpUtils.getContentType(item.getValue()));
        String disposition = "form-data";
        if (item.getValue().getType() == Type.INPUT_STREAM) {
            disposition = disposition + "; filename=\"" + item.getName() + "\"";
        }
        disposition = disposition + "; name=\"" + item.getName() + "\"";
        headers.put(CONTENT_DISPOSITION, disposition);
    }
    
    public boolean isFile() {
        return item.getValue().getType() == Type.INPUT_STREAM;
    }

    public String getValue() {
        return item.getValue().getAsString();
    }
    
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public String getContentType() {
        return headers.get(CONTENT_TYPE);
    }

    @Override
    public String getName() {
        return item.getName();
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public void write(String string) throws IOException {
        
    }

    @Override
    public void delete() throws IOException {
        
    }

    @Override
    public String getHeader(String string) {
        return headers.get(string);
    }

    @Override
    public Collection<String> getHeaders(String string) {
        return Collections.singletonList(headers.get(string));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }
    
}
