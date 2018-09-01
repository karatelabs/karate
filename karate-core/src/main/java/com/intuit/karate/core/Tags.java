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

import com.intuit.karate.ScriptBindings;
import com.intuit.karate.ScriptValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.Bindings;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 * @author pthomas3
 */
public class Tags {

    private final List<String> tags;
    private final Bindings bindings;

    public static Collection<Tag> merge(List<Tag>... lists) {
        Set<Tag> tags = new HashSet();
        for (List<Tag> list : lists) {
            if (list != null) {
                tags.addAll(list);
            }
        }
        return tags;
    }

    public static boolean evaluate(String tagSelector, Collection<Tag> tags) {
        if (tagSelector == null) {
            return true;
        }
        Tags bridge = new Tags(tags);
        return bridge.evaluate(tagSelector);
    }

    private boolean evaluate(String tagSelector) {
        ScriptValue sv = ScriptBindings.eval(tagSelector, bindings);
        return sv.isBooleanTrue();
    }

    private Tags(Collection<Tag> in) {
        if (in == null) {
            tags = Collections.EMPTY_LIST;
        } else {
            tags = new ArrayList(in.size());
            for (Tag t : in) {
                tags.add('@' + t.getText());
            }
        }
        bindings = ScriptBindings.createBindings();
        bindings.put("bridge", this);
        ScriptValue anyOfFun = ScriptBindings.eval("function(){ return bridge.anyOf(arguments) }", bindings);
        ScriptValue allOfFun = ScriptBindings.eval("function(){ return bridge.allOf(arguments) }", bindings);
        ScriptValue notFun = ScriptBindings.eval("function(){ return bridge.not(arguments) }", bindings);
        bindings.put("anyOf", anyOfFun.getValue());
        bindings.put("allOf", allOfFun.getValue());
        bindings.put("not", notFun.getValue());
    }

    public boolean anyOf(ScriptObjectMirror som) {
        for (Object s : som.values()) {
            if (tags.contains(s.toString())) {
                return true;
            }
        }
        return false;
    }

    public boolean allOf(ScriptObjectMirror som) {
        return tags.containsAll(som.values());
    }

    public boolean not(ScriptObjectMirror som) {
        return !anyOf(som);
    }

    public static List<String> toListOfStrings(Collection<Tag> tags) {
        List<String> values = new ArrayList(tags.size());
        for (Tag tag : tags) {
            values.add(tag.getText());
        }
        return values;
    }

    public static Map<String, List<String>> toMapOfNameValues(Collection<Tag> tags) {
        Map<String, List<String>> map = new HashMap(tags.size());
        for (Tag tag : tags) {
            map.put(tag.getName(), tag.getValues());
        }
        return map;
    }

    public static List<Map> toResultList(List<Tag> tags) {
        List<Map> list = new ArrayList(tags.size());
        for (Tag tag : tags) {
            Map<String, Object> tagMap = new HashMap(2);
            tagMap.put("line", tag.getLine());
            tagMap.put("name", '@' + tag.getName());
            list.add(tagMap);
        }
        return list;
    }

}
