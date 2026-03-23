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
package io.karatelabs.match;

import io.karatelabs.common.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validators {

    interface Validator extends Function<Value, Result> {
        //
    }

    static class RegexValidator implements Validator {

        private final Pattern pattern;

        public RegexValidator(String regex) {
            regex = StringUtils.trimToEmpty(regex);
            pattern = Pattern.compile(regex);
        }

        @Override
        public Result apply(Value v) {
            if (!v.isString()) {
                return Result.fail("not a string");
            }
            String strValue = v.getValue();
            Matcher matcher = pattern.matcher(strValue);
            return matcher.matches() ? Result.PASS : Result.fail("regex match failed");
        }

    }

    static final Map<String, Validator> VALIDATORS = new HashMap<>(11);

    static {
        VALIDATORS.put("array", v -> v.isList() ? Result.PASS : Result.fail("not an array or list"));
        VALIDATORS.put("boolean", v -> v.isBoolean() ? Result.PASS : Result.fail("not a boolean"));
        VALIDATORS.put("ignore", v -> Result.PASS);
        VALIDATORS.put("notnull", v -> v.isNull() ? Result.fail("null") : Result.PASS);
        VALIDATORS.put("null", v -> v.isNull() ? Result.PASS : Result.fail("not null"));
        VALIDATORS.put("number", v -> v.isNumber() ? Result.PASS : Result.fail("not a number"));
        VALIDATORS.put("object", v -> v.isMap() ? Result.PASS : Result.fail("not an object or map"));
        VALIDATORS.put("present", v -> v.isNotPresent() ? Result.fail("not present") : Result.PASS);
        VALIDATORS.put("notpresent", v -> v.isNotPresent() ? Result.PASS : Result.fail("present"));
        VALIDATORS.put("string", v -> v.isNotPresent() ? Result.fail("not present") : v.isString() ? Result.PASS : Result.fail("not a string"));
        VALIDATORS.put("uuid", v -> {
            if (!v.isString()) {
                return Result.fail("not a string");
            }
            try {
                UUID.fromString(v.getValue());
                return Result.PASS;
            } catch (Exception e) {
                return Result.fail("not a valid uuid");
            }
        });
    }

}
