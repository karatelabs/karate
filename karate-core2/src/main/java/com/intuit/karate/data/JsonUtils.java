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
package com.intuit.karate.data;

import com.intuit.karate.FileUtils;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import java.util.EnumSet;
import java.util.Set;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;

/**
 *
 * @author pthomas3
 */
public class JsonUtils {

    private JsonUtils() {
        // only static methods
    }

    static {
        // ensure that even if jackson (databind?) is on the classpath, don't switch provider
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JsonSmartJsonProvider();
            private final MappingProvider mappingProvider = new JsonSmartMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }
    
    public static boolean isJson(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (s.charAt(0) == ' ') {
            s = s.trim();
        }
        return s.charAt(0) == '{' || s.charAt(0) == '[';
    }

    public static String toStrictJson(String raw) {
        JSONParser jp = new JSONParser(JSONParser.MODE_PERMISSIVE);
        try {
            Object o = jp.parse(raw);
            return JSONValue.toJSONString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(Object o) {
        return JSONValue.toJSONString(o);
    }

    public static byte[] toJsonBytes(Object o) {
        return toJson(o).getBytes(FileUtils.UTF8);
    }

    public static Object fromJsonString(String json) {
        return JSONValue.parse(json);
    }

}
