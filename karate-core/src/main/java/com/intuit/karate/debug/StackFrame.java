/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.debug;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class StackFrame {

    private int id;
    private int line;
    private int column;
    private String name;
    private final Map<String, Object> source = new HashMap();
    
    public static StackFrame forSource(String sourceName, String sourcePath, int line) {
        StackFrame sf = new StackFrame();
        sf.line = line;
        sf.name = "main";
        sf.setSourceName(sourceName);
        sf.setSourcePath(sourcePath);
        sf.setSourceReference(0);
        return sf;
    }    

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceName() {
        return (String) source.get("name");
    }

    public void setSourceName(String name) {
        source.put("name", name);
    }

    public String getSourcePath() {
        return (String) source.get("path");
    }

    public void setSourcePath(String name) {
        source.put("path", name);
    }

    public int getSourceReference() {
        return (Integer) source.get("sourceReference");
    }

    public void setSourceReference(int reference) {
        source.put("sourceReference", reference);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("id", id);
        map.put("line", line);
        map.put("column", column);
        map.put("name", name);
        map.put("source", source);
        return map;
    }
    
}
