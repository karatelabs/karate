/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.FileUtils;
import com.intuit.karate.http.HttpConstants;
import com.intuit.karate.http.ResourceType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.servlet.http.Part;

/**
 *
 * @author pthomas3
 */
public class MockPart implements Part {

    private final Map<String, Object> map;
    private final byte[] bytes;
    private final String contentType;

    public MockPart(Map<String, Object> map) {
        this.map = map;
        Object value = map.get("value");
        if (value instanceof byte[]) {
            bytes = (byte[]) value;
            contentType = (String) map.get("contentType");
        } else {
            bytes = FileUtils.toBytes(value.toString());
            contentType = ResourceType.TEXT.contentType;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public String getName() {
        return (String) map.get("name");
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public void write(String fileName) throws IOException {

    }

    @Override
    public void delete() throws IOException {

    }

    @Override
    public String getHeader(String name) {
        if (name.equalsIgnoreCase("content-disposition")) {
            String disp = "form-data;";
            String partName = getName();
            if (partName != null) {
                disp = disp + " name=\"" + partName + "\";";
            }
            String fileName = (String) map.get("filename");
            if (fileName != null) {
                disp = disp + " filename=\"" + fileName + "\"";
            }
            return disp;
        }
        return (String) map.get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        String value = (String) map.get(name);
        if (value == null) {
            return Collections.EMPTY_LIST;
        } else {
            return Collections.singletonList(value);
        }
    }

    @Override
    public Collection<String> getHeaderNames() {
        return map.keySet();
    }

}
