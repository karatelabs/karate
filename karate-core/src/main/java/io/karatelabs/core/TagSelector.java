/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.core;

import io.karatelabs.common.StringUtils;
import io.karatelabs.gherkin.Tag;
import io.karatelabs.js.Engine;
import io.karatelabs.js.JavaCallable;
import io.karatelabs.js.SimpleObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced tag selector supporting V1-compatible expressions.
 * <p>
 * Supports:
 * <ul>
 *   <li>anyOf('@foo', '@bar') - match if any tag present</li>
 *   <li>allOf('@foo', '@bar') - match if all tags present</li>
 *   <li>not('@tag') - exclude tag</li>
 *   <li>valuesFor('@tag').isPresent - check if tag has values</li>
 *   <li>valuesFor('@tag').isAnyOf(1, 2) - check if any value matches</li>
 *   <li>valuesFor('@tag').isAllOf(1, 2) - check if all values present</li>
 *   <li>valuesFor('@tag').isOnly(1, 2) - exact value match</li>
 *   <li>valuesFor('@tag').isEach(fn) - predicate on values</li>
 *   <li>Compound expressions: anyOf('@foo') && !anyOf('@ignore')</li>
 * </ul>
 */
public class TagSelector {

    private final List<String> tagTexts;  // Without @ prefix
    private final Map<String, List<String>> tagValues;

    /**
     * Values helper class for valuesFor('@tag') functionality.
     * Implements SimpleObject for native JS engine access.
     */
    public static class Values implements SimpleObject {

        private final List<String> values;
        private final boolean isPresent;

        public Values(List<String> values) {
            this.values = values == null ? Collections.emptyList() : values;
            this.isPresent = !this.values.isEmpty();
        }

        @Override
        public Object jsGet(String name) {
            return switch (name) {
                case "isPresent" -> isPresent;
                case "isAnyOf" -> (JavaCallable) (ctx, args) -> isAnyOf(args);
                case "isAllOf" -> (JavaCallable) (ctx, args) -> isAllOf(args);
                case "isOnly" -> (JavaCallable) (ctx, args) -> isOnly(args);
                case "isEach" -> (JavaCallable) (ctx, args) -> {
                    if (args.length > 0 && args[0] instanceof JavaCallable fn) {
                        return isEach(fn);
                    }
                    return false;
                };
                default -> null;
            };
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
            List<String> list = new ArrayList<>(args.length);
            for (Object o : args) {
                list.add(o.toString());
            }
            return values.containsAll(list);
        }

        public boolean isOnly(Object... args) {
            return isAllOf(args) && args.length == values.size();
        }

        public boolean isEach(JavaCallable fn) {
            for (String s : values) {
                Object result = fn.call(null, s);
                if (!isTrue(result)) {
                    return false;
                }
            }
            return true;
        }

    }

    public TagSelector(Collection<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            this.tagTexts = Collections.emptyList();
            this.tagValues = Collections.emptyMap();
        } else {
            this.tagTexts = new ArrayList<>(tags.size());
            this.tagValues = new HashMap<>(tags.size());
            for (Tag tag : tags) {
                tagTexts.add(tag.getText());
                tagValues.put(tag.getName(), tag.getValues());
            }
        }
    }

    /**
     * Evaluate a tag selector expression against this set of tags.
     *
     * @param tagSelector the selector expression (e.g., "anyOf('@foo') && not('@ignore')")
     * @param karateEnv   the current karate.env value (may be null)
     * @return true if the tags match the selector
     */
    public boolean evaluate(String tagSelector, String karateEnv) {
        // Check for @ignore tag (always skip unless called with tag selector)
        if (containsIgnoreCase(tagTexts, Tag.IGNORE)) {
            return false;
        }

        // Check for @setup tag (only run via karate.setup())
        if (tagValues.containsKey(Tag.SETUP)) {
            return false;
        }

        // Check @env=xxx tag
        Values envValues = valuesFor(Tag.ENV);
        if (envValues.isPresent) {
            if (karateEnv == null) {
                return false;
            }
            if (!envValues.isAnyOf(karateEnv)) {
                return false;
            }
        }

        // Check @envnot=xxx tag
        if (karateEnv != null && valuesFor(Tag.ENVNOT).isAnyOf(karateEnv)) {
            return false;
        }

        // If no tag selector, all non-ignored scenarios pass
        if (tagSelector == null) {
            return true;
        }

        // Evaluate the tag selector expression using JS engine
        Engine engine = new Engine();
        engine.put("anyOf", (JavaCallable) (ctx, args) -> anyOf(args));
        engine.put("allOf", (JavaCallable) (ctx, args) -> allOf(args));
        engine.put("not", (JavaCallable) (ctx, args) -> not(args));
        engine.put("valuesFor", (JavaCallable) (ctx, args) -> {
            String name = args.length > 0 ? args[0].toString() : "";
            return valuesFor(name);
        });

        Object result = engine.eval(tagSelector);
        return isTrue(result);
    }

    public boolean anyOf(Object... values) {
        for (String s : removeTagPrefixes(values)) {
            if (tagTexts.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean allOf(Object... values) {
        return tagTexts.containsAll(removeTagPrefixes(values));
    }

    public boolean not(Object... values) {
        return !anyOf(values);
    }

    public Values valuesFor(String name) {
        List<String> list = tagValues.get(removeTagPrefix(name));
        return new Values(list);
    }

    public boolean contains(String tagText) {
        return tagTexts.contains(removeTagPrefix(tagText));
    }

    public List<String> getTagTexts() {
        return tagTexts;
    }

    public Map<String, List<String>> getTagValues() {
        return tagValues;
    }

    private static String removeTagPrefix(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '@') {
            return s.substring(1);
        }
        return s;
    }

    private static Collection<String> removeTagPrefixes(Object... values) {
        List<String> list = new ArrayList<>(values.length);
        for (Object o : values) {
            list.add(removeTagPrefix(o.toString()));
        }
        return list;
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        return true;
    }

    /**
     * Convert Karate/Cucumber-style tag expressions to JS-style tag selector.
     * <p>
     * Examples:
     * <ul>
     *   <li>"@foo" → "anyOf('@foo')"</li>
     *   <li>"@foo, @bar" → "anyOf('@foo','@bar')"</li>
     *   <li>"@foo", "@bar" (multiple args) → "anyOf('@foo') && anyOf('@bar')"</li>
     *   <li>"~@ignore" → "not('@ignore')"</li>
     *   <li>"anyOf('@foo')" (already JS-style) → "anyOf('@foo')"</li>
     * </ul>
     *
     * @param tags the tag expressions
     * @return the JS-style tag selector, or null if no tags
     */
    public static String fromKarateOptionsTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return fromKarateOptionsTags(tags.toArray(new String[0]));
    }

    /**
     * Convert Karate/Cucumber-style tag expressions to JS-style tag selector.
     *
     * @param tags the tag expressions
     * @return the JS-style tag selector, or null if no tags
     */
    public static String fromKarateOptionsTags(String... tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }

        // Detect if already using new JS-style syntax
        for (String s : tags) {
            if (s.indexOf('(') != -1) {
                // Enhanced tag expression detected, use as-is
                return s;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.length; i++) {
            String and = tags[i].trim();
            if (and.startsWith("~")) {
                // Negation: ~@tag
                sb.append("not('").append(and.substring(1)).append("')");
            } else {
                // OR list: @foo,@bar becomes anyOf('@foo','@bar')
                sb.append("anyOf(");
                List<String> or = StringUtils.split(and, ',', false);
                for (String tag : or) {
                    sb.append('\'').append(tag.trim()).append('\'').append(',');
                }
                sb.setLength(sb.length() - 1);  // Remove trailing comma
                sb.append(')');
            }
            if (i < (tags.length - 1)) {
                sb.append(" && ");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return tagTexts.toString();
    }

}
