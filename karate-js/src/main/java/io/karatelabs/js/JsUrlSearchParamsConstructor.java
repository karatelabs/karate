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
import java.util.List;
import java.util.Map;

/**
 * Global {@code URLSearchParams} constructor: from a query string (a leading {@code ?} dropped),
 * another URLSearchParams, an iterable of {@code [name, value]} pairs, or a record of names to values.
 */
class JsUrlSearchParamsConstructor extends JsFunction {

    JsUrlSearchParamsConstructor() {
        this.name = "URLSearchParams";
        this.length = 0;
        defineOwn("prototype", JsUrlSearchParamsPrototype.INSTANCE, PropertySlot.INTRINSIC);
    }

    @Override
    public Object call(Context context, Object[] args) {
        CallInfo callInfo = context.getCallInfo();
        if (callInfo == null || !callInfo.constructor) {
            throw JsErrorException.typeError("Constructor URLSearchParams requires 'new'");
        }
        CoreContext cc = context instanceof CoreContext c ? c : null;
        JsUrlSearchParams params = new JsUrlSearchParams(null);
        Object init = args.length > 0 ? args[0] : Terms.UNDEFINED;
        if (init == null || init == Terms.UNDEFINED) {
            return params;
        }
        if (init instanceof JsUrlSearchParams other) {
            for (String[] pair : other.pairs) {
                params.pairs.add(pair.clone());
            }
        } else if (init instanceof List || (init instanceof ObjectLike ol && ol.getMember(IterUtils.SYMBOL_ITERATOR) instanceof JsCallable)) {
            JsIterator it = IterUtils.getIterator(init, context);
            while (it.hasNext()) {
                Object entry = it.next();
                List<Object> pair = new ArrayList<>(2);
                if (entry instanceof List<?> list) {
                    pair.addAll(list);
                } else if (entry instanceof ObjectLike) {
                    JsIterator inner = IterUtils.getIterator(entry, context);
                    while (inner.hasNext()) {
                        pair.add(inner.next());
                    }
                }
                if (pair.size() != 2) {
                    throw JsErrorException.typeError("Each query pair must be an iterable [name, value] tuple");
                }
                params.pairs.add(new String[]{Terms.toStringCoerce(pair.get(0), cc), Terms.toStringCoerce(pair.get(1), cc)});
            }
        } else if (init instanceof ObjectLike || init instanceof Map) {
            Map<?, ?> map = init instanceof Map<?, ?> m ? m : ((ObjectLike) init).toMap();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                params.pairs.add(new String[]{String.valueOf(e.getKey()), Terms.toStringCoerce(e.getValue(), cc)});
            }
        } else {
            String query = Terms.toStringCoerce(init, cc);
            params.parse(query.startsWith("?") ? query.substring(1) : query);
        }
        return params;
    }

}
