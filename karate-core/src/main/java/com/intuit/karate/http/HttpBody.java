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

import com.intuit.karate.FileUtils;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpBody {
    
    private final byte[] bytes;
    private final InputStream stream;
    private final MultiValuedMap fields;
    private final List<MultiPartItem> parts;
    private final String contentType;
    
    public boolean isStream() {
        return stream != null;
    }
    
    public boolean isUrlEncoded() {
        return fields != null;
    }
    
    public boolean isMultiPart() {
        return parts != null;
    }

    public byte[] getBytes() {
        if (isStream()) {
            return FileUtils.toBytes(stream);
        }
        return bytes;
    }

    public InputStream getStream() {
        return stream;
    }

    public String getContentType() {
        return contentType;
    }

    public List<MultiPartItem> getParts() {
        return parts;
    }        

    public Map<String, String[]> getParameters() {
        if (fields == null) {
            return Collections.EMPTY_MAP;
        }
        Map<String, String[]> map = new LinkedHashMap<>(fields.size());
        for (Map.Entry<String, List> entry : fields.entrySet()) {
            List list = entry.getValue();
            String[] values = new String[list.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = list.get(i) + "";
            }
            map.put(entry.getKey(), values);
        }
        return map;
    }
    
    private HttpBody(byte[] bytes, InputStream stream, String contentType) {
        this.bytes = bytes;
        this.stream = stream;
        this.contentType = contentType;
        this.fields = null;
        this.parts = null;
    }
    
    private HttpBody(MultiValuedMap fields, String contentType) {
        this.bytes = null;
        this.stream = null;
        this.contentType = contentType;
        this.fields = fields;
        this.parts = null;
    }
    
    private HttpBody(List<MultiPartItem> parts, String contentType) {
        this.bytes = null;
        this.stream = null;
        this.contentType = contentType;
        this.fields = null;
        this.parts = parts;
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
    
    public static HttpBody formFields(MultiValuedMap fields, String contentType) {
        return new HttpBody(fields, contentType);
    }
    
    public static HttpBody multiPart(List<MultiPartItem> parts, String contentType) {
        return new HttpBody(parts, contentType);
    }
    
}
