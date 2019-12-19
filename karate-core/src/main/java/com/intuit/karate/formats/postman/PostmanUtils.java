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
package com.intuit.karate.formats.postman;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by rkumar32 on 7/5/17.
 */
public class PostmanUtils {

    private static final Logger logger = LoggerFactory.getLogger(PostmanUtils.class);

    private PostmanUtils() {
        // only static methods
    }

    public static String toKarateFeature(List<PostmanItem> items) {
        return toKarateFeature(UUID.randomUUID().toString(), items);
    }

    public static String toKarateFeature(String collectionName, List<PostmanItem> items) {
        StringBuilder sb = new StringBuilder("Feature: ").append(collectionName);
        sb.append(System.lineSeparator()).append(System.lineSeparator());
        for (PostmanItem item : items) {
            sb.append(item.convert());
        }
        return sb.toString();
    }

    public static List<PostmanItem> readPostmanJson(String json) {
        DocumentContext doc = JsonPath.parse(json);
        List<Map<String, Object>> list = (List) doc.read("$.item");
        return readPostmanItems(null, list);
    }

    private static List<PostmanItem> readPostmanItems(PostmanItem parent, List<Map<String, Object>> list) {
        PostmanItem item;
        List<PostmanItem> requests = new ArrayList<>(list.size());
        for (Map<String, Object> map : list) {
            logger.debug("map: {}", map);
            item = readPostmanItem(parent, map);
            requests.add(item);
        }
        return requests;
    }

    private static PostmanItem readPostmanItem(PostmanItem parent, Map<String, Object> itemMap) {
        PostmanItem item = new PostmanItem();
        String name = (String) itemMap.get("name");
        item.setName(name);
        item.setParent(Optional.ofNullable(parent));
        Map<String, Object> requestInfo = (Map) itemMap.get("request");
        if (requestInfo != null) {
            item.setRequest(readPostmanRequest(requestInfo));
        } else { // this may have list of sub-items
            List<PostmanItem> subItems = readPostmanItems(item, (List<Map<String, Object>>) itemMap.get("item"));
            item.setItems(Optional.of(subItems));
        }
        return item;
    }

    private static PostmanRequest readPostmanRequest(Map<String, Object> requestInfo) {
        String url = getUrl(requestInfo.get("url"));
        String method = (String) requestInfo.get("method");
        List<Map<String, Object>> headersList = (List) requestInfo.get("header");
        Map<String, String> headers = new HashMap<>();
        if (headersList != null) {
            for (Map<String, Object> header : headersList) {
                headers.put((String) header.get("key"), (String) header.get("value"));
            }
        }
        Map<String, Object> bodyInfo = (Map) requestInfo.get("body");
        String body = null;
        if (bodyInfo != null) {
            if (bodyInfo.containsKey("raw")) {
                body = ((String) bodyInfo.get("raw")).replace(System.lineSeparator(), "");
            } else if (bodyInfo.containsKey("formdata")) {
                body = ((List) bodyInfo.get("formdata")).toString().replace(System.lineSeparator(), "");
            }
        }
        PostmanRequest request = new PostmanRequest();
        request.setUrl(url);
        request.setMethod(method);
        request.setHeaders(headers);
        request.setBody(body);
        return request;
    }

    private static String getUrl(Object url) {
        return (url instanceof String) ? (String) url : (String) ((Map) url).get("raw");
    }

}
