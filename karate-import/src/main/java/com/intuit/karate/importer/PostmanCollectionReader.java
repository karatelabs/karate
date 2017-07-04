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
package com.intuit.karate.importer;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class PostmanCollectionReader {
    
    private static final Logger logger = LoggerFactory.getLogger(PostmanCollectionReader.class);
    
    private PostmanCollectionReader() {
        // only static methods
    }
    
    public static List<PostmanRequest> parse(String path) {
        File file = new File(path);        
        String json;
        try {
            json = FileUtils.readFileToString(file, "utf-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        DocumentContext doc = JsonPath.parse(json);
        List<Map<String, Object>> list = (List) doc.read("$.item");
        List<PostmanRequest> requests = new ArrayList<>(list.size());
        for (Map<String, Object> map : list) {
            logger.debug("map: {}", map);
            String name = (String) map.get("name");
            PostmanRequest request = new PostmanRequest();
            request.setName(name);
            requests.add(request);
        }
        return requests;
    }
    
}
