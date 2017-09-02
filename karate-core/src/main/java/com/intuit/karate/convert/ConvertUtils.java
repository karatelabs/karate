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
package com.intuit.karate.convert;

import com.intuit.karate.FileUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by rkumar32 on 7/5/17.
 */
public class ConvertUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(ConvertUtils.class);

    private ConvertUtils() {
        // only static methods
    }

    public static File write(List<PostmanRequest> requests, String path) {
        String feature = toKarateFeature(requests);
        String inputFileName = new File(path).getName();
        String outputFileName = inputFileName.replace("postman_collection", "feature");
        String dirPath = new File(path).getParentFile().getPath();
        File featureFile;
        featureFile = new File(dirPath + "/" + outputFileName);
        FileUtils.writeToFile(featureFile, feature);
        return featureFile;
    }

    public static String toKarateFeature(List<PostmanRequest> requests) {
        String scenarios = "";
        for (PostmanRequest request : requests) {
            scenarios += request.convert();
        }
        String feature = "Feature: \n\n" + scenarios;
        return feature;
    }
    
    public static List<PostmanRequest> readPostmanJson(String json) {
        DocumentContext doc = JsonPath.parse(json);
        List<Map<String, Object>> list = (List) doc.read("$.item");
        List<PostmanRequest> requests = new ArrayList<>(list.size());
        for (Map<String, Object> map : list) {
            logger.debug("map: {}", map);
            String name = (String) map.get("name");
            Map<String, Object> requestInfo = (Map) map.get("request");
            String url = (String) requestInfo.get("url");
            String method = (String) requestInfo.get("method");
            List<Map<String, Object>> headersList = (List) requestInfo.get("header");
            Map<String, String> headers = new HashMap<>();
            for (Map<String, Object> header : headersList) {
                headers.put((String) header.get("key"), (String) header.get("value"));
            }
            Map<String, Object> bodyInfo = (Map) requestInfo.get("body");
            String body = null;
            if (bodyInfo.containsKey("raw")) {
                body = ((String) bodyInfo.get("raw")).replace(System.lineSeparator(), "");
            }
            else if (bodyInfo.containsKey("formdata")) {
                body = ((List) bodyInfo.get("formdata")).toString().replace(System.lineSeparator(), "");
            }
            PostmanRequest request = new PostmanRequest();
            request.setName(name);
            request.setUrl(url);
            request.setMethod(method);
            request.setHeaders(headers);
            request.setBody(body);
            requests.add(request);
        }
        return requests;
    }    
    
}
