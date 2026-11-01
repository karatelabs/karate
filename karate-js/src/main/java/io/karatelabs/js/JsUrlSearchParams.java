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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * WHATWG URLSearchParams: an ordered list of name-value pairs. One owned by a {@link JsUrl}
 * writes its serialization back to the URL's query on every change.
 */
class JsUrlSearchParams extends JsObject {

    final List<String[]> pairs = new ArrayList<>();
    private final JsUrl url;

    JsUrlSearchParams(JsUrl url) {
        super(null, JsUrlSearchParamsPrototype.INSTANCE);
        this.url = url;
    }

    void reset(String query) {
        pairs.clear();
        if (query != null) {
            parse(query);
        }
    }

    void parse(String query) {
        for (String part : query.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String name = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : part.substring(eq + 1);
            pairs.add(new String[]{decode(name), decode(value)});
        }
    }

    private static String decode(String s) {
        return new String(JsUrl.percentDecode(s.replace('+', ' ')), StandardCharsets.UTF_8);
    }

    void changed() {
        if (url != null) {
            String serialized = serialize();
            url.query = serialized.isEmpty() ? null : serialized;
        }
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        for (String[] pair : pairs) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(JsUrl.encodeForm(pair[0])).append('=').append(JsUrl.encodeForm(pair[1]));
        }
        return sb.toString();
    }

}
