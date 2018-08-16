/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeatureResult extends HashMap<String, Object> {
    
    private final String name;
    private final List<ResultElement> elements = new ArrayList();
    private final List<TagResult> tags;
    private final String packageQualifiedName;
    
    public FeatureResult(Feature feature) {
        put("elements", elements);
        put("keyword", "Feature");
        put("line", feature.getLine());
        String relativePath = feature.getRelativePath();
        put("uri", relativePath);
        put("name", relativePath); // hack for json / html report
        packageQualifiedName = FileUtils.toPackageQualifiedName(relativePath);
        name = feature.getName();
        put("id", StringUtils.toIdString(feature.getName()));
        String temp = feature.getName() == null ? "" : feature.getName();
        if (feature.getDescription() != null) {
            temp = temp + "\n" + feature.getDescription();
        }
        put("description", temp.trim());       
        List<Tag> list = feature.getTags();
        if (list != null) {
            tags = new ArrayList(list.size());
            put("tags", tags);
            for (Tag tag : list) {
                tags.add(new TagResult(tag));                
            }
        } else {
           tags = Collections.EMPTY_LIST;
        }
    }    

    public String getPackageQualifiedName() {
        return packageQualifiedName;
    }        

    public List<TagResult> getTags() {
        return tags;
    }
    
    public void addResult(ResultElement element) {
        elements.add(element);
    }

    public String getName() {
        return name;
    }

    public List<ResultElement> getElements() {
        return elements;
    }
    
}
