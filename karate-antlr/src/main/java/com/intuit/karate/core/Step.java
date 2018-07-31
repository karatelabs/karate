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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Step {
    
    private int line;
    private String prefix;
    private String text;
    private String docString;
    private Table table;
    
    public static List<Map<String, Object>> toList(List<Step> steps) {
        if (steps == null) {
            return Collections.EMPTY_LIST;
        }
        List<Map<String, Object>> list = new ArrayList(steps.size());
        for (Step step : steps) {
            list.add(step.toMap());
        }
        return list;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("line", line);
        map.put("keyword", prefix);
        map.put("name", text);
        return map;        
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }    
    
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocString() {
        return docString;
    }

    public void setDocString(String docString) {
        this.docString = docString;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }
    
    @Override
    public String toString() {
        String temp = prefix + " " + text;
        if (docString != null) {
            temp  = temp + "\n\"\"\"" + docString + "\n\"\"\"";
        } else if (table != null) {
            temp = temp + " " + table.toString();
        }
        return temp;
    }    
   
}
