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

import io.karatelabs.js.Context;
import io.karatelabs.js.Engine;

import java.util.function.BiConsumer;

public class Match {

    public enum Type {

        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        CONTAINS_ONLY,
        CONTAINS_ANY,
        CONTAINS_DEEP,
        CONTAINS_ONLY_DEEP,
        CONTAINS_ANY_DEEP,
        EACH_EQUALS,
        EACH_NOT_EQUALS,
        EACH_CONTAINS,
        EACH_NOT_CONTAINS,
        EACH_CONTAINS_ONLY,
        EACH_CONTAINS_ANY,
        EACH_CONTAINS_DEEP,
        WITHIN,
        NOT_WITHIN

    }

    public static Value evaluate(Object actual, Context context, BiConsumer<Context, Result> onResult) {
        return new Value(Value.parseIfJsonOrXmlString(actual), context, onResult);
    }

    public static Value that(Object actual) {
        return new Value(Value.parseIfJsonOrXmlString(actual), null, (context, result) -> {
            if (!result.pass) {
                throw new RuntimeException(result.message);
            }
        });
    }

    public static Result execute(Engine engine, Type matchType, Object actual, Object expected) {
        return execute(engine, matchType, actual, expected, false);
    }

    public static Result execute(Engine engine, Type matchType, Object actual, Object expected, boolean matchEachEmptyAllowed) {
        Value actualValue = new Value(actual);
        Value expectedValue = new Value(expected);
        Operation op = new Operation(engine, matchType, actualValue, expectedValue, matchEachEmptyAllowed);
        op.execute();
        return op.getResult();
    }

    public static Result execute(Engine engine, Type matchType, Object actual, Object expected, long memoryThreshold) {
        Value actualValue = new Value(actual, null, null, true, memoryThreshold);
        Value expectedValue = new Value(expected, null, null, true, memoryThreshold);
        Operation op = new Operation(engine, matchType, actualValue, expectedValue);
        op.execute();
        return op.getResult();
    }

}
