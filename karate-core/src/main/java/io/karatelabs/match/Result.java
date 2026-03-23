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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Result {

    /**
     * Represents a single failure in a match operation with structured data.
     */
    public record Failure(
            String path,           // e.g., "$.foo[0].bar"
            String reason,         // e.g., "not equal"
            Value.Type actualType,
            Value.Type expectedType,
            Object actualValue,    // raw value
            Object expectedValue,  // raw value
            int depth
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("path", path);
            map.put("reason", reason);
            map.put("actualType", actualType != null ? actualType.name() : null);
            map.put("expectedType", expectedType != null ? expectedType.name() : null);
            map.put("actualValue", actualValue);
            map.put("expectedValue", expectedValue);
            map.put("depth", depth);
            return map;
        }
    }

    public static final Result PASS = new Result(true, null, List.of());

    public static Result fail(String message) {
        return new Result(false, message, List.of());
    }

    public static Result fail(String message, List<Failure> failures) {
        return new Result(false, message, failures);
    }

    public final String message;
    public final boolean pass;
    public final List<Failure> failures;

    Result(boolean pass, String message) {
        this(pass, message, List.of());
    }

    Result(boolean pass, String message, List<Failure> failures) {
        this.pass = pass;
        this.message = message;
        this.failures = failures != null ? failures : List.of();
    }

    @Override
    public String toString() {
        return pass ? "[pass]" : message;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>(4);
        map.put("pass", pass);
        map.put("message", message);
        if (!failures.isEmpty()) {
            List<Map<String, Object>> failuresList = new ArrayList<>(failures.size());
            for (Failure f : failures) {
                failuresList.add(f.toMap());
            }
            map.put("failures", failuresList);
        }
        return map;
    }

}
