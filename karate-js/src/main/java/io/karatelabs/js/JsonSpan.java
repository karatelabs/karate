/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON value with its source offsets, from {@link JsonParser#parseSpans(String)}.
 * {@code start} is inclusive and {@code end} exclusive, as Java UTF-16 indexes into
 * the parsed text; a string's span includes its quotes, a container's runs from its
 * opening to its closing bracket, and no span includes whitespace. The span text is
 * the raw literal ({@code 1.0}, {@code 1e3}, {@code -0}); {@code value} is the decoded form.
 *
 * @param members  OBJECT only: every member in source order, duplicates kept; else empty
 * @param elements ARRAY only: in source order; else empty
 * @param value    a scalar decoded exactly as {@code parse()} does; a container: null
 */
public record JsonSpan(Kind kind, int start, int end,
                       List<Member> members,
                       List<JsonSpan> elements,
                       Object value) {

    public enum Kind {OBJECT, ARRAY, STRING, NUMBER, TRUE, FALSE, NULL}

    /**
     * An object member: {@code key} is decoded, and the key span is the quoted literal.
     */
    public record Member(String key, int keyStart, int keyEnd, JsonSpan value) {
    }

    /**
     * {@code bom} is true when the text starts with U+FEFF, which counts as index 0.
     */
    public record Doc(boolean bom, JsonSpan root) {
    }

    public String text(String source) {
        return source.substring(start, end);
    }

    /**
     * What {@link JsonParser#parse(String)} returns for this node; for duplicate
     * keys the last one wins.
     */
    public Object toValue() {
        switch (kind) {
            case OBJECT:
                Map<String, Object> map = new LinkedHashMap<>();
                for (Member m : members) {
                    map.put(m.key, m.value.toValue());
                }
                return map;
            case ARRAY:
                List<Object> list = new ArrayList<>(elements.size());
                for (JsonSpan e : elements) {
                    list.add(e.toValue());
                }
                return list;
            default:
                return value;
        }
    }

}
