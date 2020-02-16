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

import com.intuit.karate.http.HttpLogModifier;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

/**
 *
 * @author pthomas3
 */
public class LoggingUtils {
    
    private LoggingUtils() {
        // only static methods
    }
    
    private static Collection<String> sortKeys(Header[] headers) {
        Set<String> keys = new TreeSet<>();
        for (Header header : headers) {
            keys.add(header.getName());
        }
        return keys;
    }

    private static void logHeaderLine(HttpLogModifier logModifier, StringBuilder sb, int id, char prefix, String key, Header[] headers) {
        sb.append(id).append(' ').append(prefix).append(' ').append(key).append(": ");
        if (headers.length == 1) {
            if (logModifier == null) {
                sb.append(headers[0].getValue());
            } else {
                sb.append(logModifier.header(key, headers[0].getValue()));
            }
        } else {
            List<String> list = new ArrayList(headers.length);
            for (Header header : headers) {
                if (logModifier == null) {
                    list.add(header.getValue());
                } else {
                    list.add(logModifier.header(key, header.getValue()));
                }
            }
            sb.append(list);
        }
        sb.append('\n');       
    }
    
    public static void logHeaders(HttpLogModifier logModifier, StringBuilder sb, int id, char prefix, org.apache.http.HttpRequest request, HttpRequest actual) {
        for (String key : sortKeys(request.getAllHeaders())) {
            Header[] headers = request.getHeaders(key);
            logHeaderLine(logModifier, sb, id, prefix, key, headers);
            for (Header header : headers) {
                actual.addHeader(header.getName(), header.getValue());
            }
        }
    }
    
    public static void logHeaders(HttpLogModifier logModifier, StringBuilder sb, int id, char prefix, HttpResponse response) {
        for (String key : sortKeys(response.getAllHeaders())) {
            Header[] headers = response.getHeaders(key);
            logHeaderLine(logModifier, sb, id, prefix, key, headers);
        }
    } 
    
    public static boolean isPrintable(HttpEntity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getContentType() != null
                    && HttpUtils.isPrintable(entity.getContentType().getValue());
    }
    
}
