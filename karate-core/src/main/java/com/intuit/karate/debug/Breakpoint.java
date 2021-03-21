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

import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.Step;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Breakpoint {        
    
    private static int nextId;
    
    public final int id;
    public final int line;
    public final boolean verified;
    public final String condition;
    
    public Breakpoint(Map<String, Object> map) {
        id = ++nextId;
        line = (Integer) map.get("line");
        verified = true;

        String breakpointCondition = (String) map.get("condition");
        if (breakpointCondition != null) {
            // remove Cucumber prefix
            String conditionText = breakpointCondition.trim();
            for (String prefix : Step.PREFIXES) {
                if (conditionText.startsWith(prefix)) {
                    conditionText = conditionText.substring(prefix.length());
                    break;
                }
            }
            conditionText = conditionText.trim();

            // if docstring to get the syntax highlight, remove the triple quotes
            if (conditionText.startsWith(FeatureParser.TRIPLE_QUOTES) && conditionText.endsWith(FeatureParser.TRIPLE_QUOTES)) {
                conditionText = conditionText.substring(FeatureParser.TRIPLE_QUOTES.length());
                conditionText = conditionText.substring(0, (conditionText.length() - FeatureParser.TRIPLE_QUOTES.length()));
            }
            condition = conditionText.trim();
        } else {
            condition = null;
        }

    }

    public int getId() {
        return id;
    }

    public int getLine() {
        return line;
    }

    public static int getNextId() {
        return nextId;
    }        

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("id", id);
        map.put("line", line);
        map.put("verified", verified);
        return map;
    }
        
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[id: ").append(id);
        sb.append(", line: ").append(line);
        sb.append(", verified: ").append(verified);
        sb.append("]");
        return sb.toString();
    }    
    
}
