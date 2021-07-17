/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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

import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import com.intuit.karate.graal.Methods;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.Value;

/**
 *
 * @author pthomas3
 */
public class Tags implements Iterable<Tag> {

    public static final Tags EMPTY = new Tags(Collections.EMPTY_LIST);

    private final Collection<Tag> original;
    private final List<String> tags;
    private Map<String, List<String>> tagValues;

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

        public boolean isAnyOf(Object... args) {
            for (Object o : args) {
                if (values.contains(o.toString())) {
                    return true;
                }
            }
            return false;
        }

        public boolean isAllOf(Object... args) {
            List list = new ArrayList(args.length);
            for (Object o : args) {
                list.add(o.toString());
            }
            return values.containsAll(list);
        }

        public boolean isOnly(Object... args) {
            return isAllOf(args) && args.length == values.size();
        }

        public boolean isEach(Value v) {
            if (!v.canExecute()) {
                return false;
            }
            for (String s : values) {                
                JsValue jv = new JsValue(JsEngine.execute(v, s));
                if (!jv.isTrue()) {
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
    }

    public boolean evaluate(String tagSelector, String karateEnv) {
        if (tags.contains(Tag.IGNORE)) {
            return false;
        }
        Values envValues = valuesFor(Tag.ENV);
        if (envValues.isPresent) {
            if (karateEnv == null) {
                return false;
            }
            if (!envValues.isAnyOf(karateEnv)) {
                return false;
            }
        }
        if (karateEnv != null && valuesFor(Tag.ENVNOT).isAnyOf(karateEnv)) {
            return false;
        }
        if (tagSelector == null) {
            return true;
        }
        JsEngine je = JsEngine.global();
        je.put("anyOf", (Methods.FunVar) this::anyOf);
        je.put("allOf", (Methods.FunVar) this::allOf);
        je.put("not", (Methods.FunVar) this::not);
        je.put("valuesFor", (Function<String, Values>) this::valuesFor);
        JsValue jv = je.eval(tagSelector);
        return jv.isTrue();
    }

    public boolean anyOf(Object... values) {
        for (String s : removeTagPrefixes(values)) {
            if (tags.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean allOf(Object... values) {
        return tags.containsAll(removeTagPrefixes(values));
    }

    public boolean not(Object... values) {
        return !anyOf(values);
    }

    public Values valuesFor(String name) {
        List<String> list = tagValues.get(removeTagPrefix(name));
        return new Values(list);
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

    private static String removeTagPrefix(String s) {
        if (s.charAt(0) == '@') {
            return s.substring(1);
        } else {
            return s;
        }
    }

    private static Collection<String> removeTagPrefixes(Object... values) {
        List<String> list = new ArrayList(values.length);
        for (Object o : values) {
            list.add(removeTagPrefix(o.toString()));
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
                    sb.append('\'').append(tag.trim()).append('\'').append(',');
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
