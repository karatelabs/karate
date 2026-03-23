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
package io.karatelabs.js;

import io.karatelabs.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public interface SimpleObject extends ObjectLike {

    Logger logger = LoggerFactory.getLogger(SimpleObject.class);

    String TO_STRING = "toString";

    @Override
    default void putMember(String name, Object value) {
        logger.warn("putMember() not implemented for: {} - {}", name, getClass().getName());
    }

    @Override
    default void removeMember(String name) {
        logger.warn("removeMember() not implemented for: {} - {}", name, getClass().getName());
    }

    @Override
    default Map<String, Object> toMap() {
        return toMap(jsKeys(), this);
    }

    default Collection<String> jsKeys() {
        logger.warn("jsKeys() not implemented for: {}", getClass().getName());
        return Collections.emptyList();
    }

    @Override
    default Object getMember(String name) {
        if (TO_STRING.equals(name)) {
            return jsToString();
        }
        return jsGet(name);
    }

    Object jsGet(String name);

    default JavaCallable jsToString() {
        try {
            Object temp = jsGet(TO_STRING);
            if (temp instanceof JavaCallable jsc) {
                return jsc;
            }
        } catch (Exception e) {
            // ignore
        }
        return (context, args) -> toString(toMap());
    }

    static String toString(Map<String, Object> map) {
        return StringUtils.formatJson(map, false, false, false);
    }

    static Map<String, Object> toMap(Collection<String> keys, SimpleObject so) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : keys) {
            map.put(key, so.jsGet(key));
        }
        return map;
    }

}
