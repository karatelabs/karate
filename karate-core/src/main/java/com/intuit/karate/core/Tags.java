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
import com.intuit.karate.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.Bindings;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

/**
 *
 * @author pthomas3
 */
public class Tags implements Iterable<Tag> {

    public static final Tags EMPTY = new Tags(Collections.EMPTY_LIST);

    private final Collection<Tag> original;
    private final List<String> tags;
    private Map<String, List<String>> tagValues;
    private final Bindings bindings;

    @Override
    public Iterator<Tag> iterator() {
        return original.iterator();
    }

    public static class Values {

        public final List<String> values;
        public final boolean isPresent;

        public Values(List<String> values) {
            this.values = values == null ? Collections.EMPTY_LIST : values;
            isPresent = !this.values.isEmpty();
        }
        
        public boolean isPresent() {
            return isPresent;
        }
        
        public boolean isAnyOf(String ... args) {
            for (String s : args) {
                if (values.contains(s)) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean isAllOf(String ... args) {
            return values.containsAll(Arrays.asList(args));
        }
        
        public boolean isOnly(String ... args) {
            return isAllOf(args) && args.length == values.size();
        }
        
        public boolean isEach(ScriptObjectMirror som) {
            if (!som.isFunction()) {
                return false;
            }            
            for (String s : values) {
                Object o = som.call(som, s);
                ScriptValue sv = new ScriptValue(o);
                if (!sv.isBooleanTrue()) {
                    return false;
                }
            }
            return true;
        }

    }

    public static Tags merge(List<Tag>... lists) {
        Set<Tag> tags = new HashSet();
        for (List<Tag> list : lists) {
            if (list != null) {
                tags.addAll(list);
            }
        }
        return new Tags(tags);
    }

    public boolean evaluate(String tagSelector) {
        if (tagSelector == null) {
            return true;
        }
        ScriptValue sv = ScriptBindings.eval(tagSelector, bindings);
        return sv.isBooleanTrue();
    }

    public boolean contains(String tagText) {
        return tags.contains(removeTagPrefix(tagText));
    }

    public List<String> getTags() {
        return tags;
    }
    
    public Collection<String> getTagKeys() {
        return tagValues.keySet();
    }

    public Map<String, List<String>> getTagValues() {
        return tagValues;
    }

    public Collection<Tag> getOriginal() {
        return original;
    }        

    public Tags(Collection<Tag> in) {
        if (in == null) {
            original = Collections.EMPTY_LIST;
            tags = Collections.EMPTY_LIST;
        } else {
            original = in;
            tags = new ArrayList(in.size());
            tagValues = new HashMap(in.size());
            for (Tag tag : in) {
                tags.add(tag.getText());
                tagValues.put(tag.getName(), tag.getValues());
            }
        }
        bindings = ScriptBindings.createBindings();
        bindings.put("bridge", this);
        ScriptValue anyOfFun = ScriptBindings.eval("function(){ return bridge.anyOf(arguments) }", bindings);
        ScriptValue allOfFun = ScriptBindings.eval("function(){ return bridge.allOf(arguments) }", bindings);
        ScriptValue notFun = ScriptBindings.eval("function(){ return bridge.not(arguments) }", bindings);
        ScriptValue valuesForFun = ScriptBindings.eval("function(s){ return bridge.valuesFor(s) }", bindings);
        bindings.put("anyOf", anyOfFun.getValue());
        bindings.put("allOf", allOfFun.getValue());
        bindings.put("not", notFun.getValue());
        bindings.put("valuesFor", valuesForFun.getValue());
    }

    private static String removeTagPrefix(String s) {
        if (s.charAt(0) == '@') {
            return s.substring(1);
        } else {
            return s;
        }
    }

    private static Collection<String> removeTagPrefix(Collection<Object> c) {
        List<String> list = new ArrayList(c.size());
        for (Object o : c) {
            String s = o.toString();
            list.add(removeTagPrefix(s));
        }
        return list;
    }

    public boolean anyOf(ScriptObjectMirror som) {
        for (String s : removeTagPrefix(som.values())) {
            if (tags.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean allOf(ScriptObjectMirror som) {
        return tags.containsAll(removeTagPrefix(som.values()));
    }

    public boolean not(ScriptObjectMirror som) {
        return !anyOf(som);
    }

    public Values valuesFor(String name) {
        List<String> list = tagValues.get(removeTagPrefix(name));
        return new Values(list);
    }

    public static List<Map> toResultList(List<Tag> tags) {
        List<Map> list = new ArrayList(tags.size());
        for (Tag tag : tags) {
            Map<String, Object> tagMap = new HashMap(2);
            tagMap.put("line", tag.getLine());
            tagMap.put("name", '@' + tag.getText());
            list.add(tagMap);
        }
        return list;
    }
    
    public static String fromKarateOptionsTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return fromKarateOptionsTags(tags.toArray(new String[]{}));
    }    
    
    public static String fromKarateOptionsTags(String... tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        for (String s : tags) {
            if (s.indexOf('(') != -1) { // new enhanced tag expression detected !
                return s;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.length; i++) {
            String and = StringUtils.trimToEmpty(tags[i]);
            if (and.startsWith("~")) {
                sb.append("not('").append(and.substring(1)).append("')");
            } else {
                sb.append("anyOf(");
                List<String> or = StringUtils.split(and, ',', false);
                for (String tag : or) {
                    sb.append('\'').append(tag).append('\'').append(',');
                }
                sb.setLength(sb.length() - 1);
                sb.append(')');
            }
            if (i < (tags.length - 1)) {
                sb.append(" && ");
            }
        }
        return sb.toString();
    }    

}
