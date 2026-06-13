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

public enum EventType {

    CONTEXT_ENTER,
    CONTEXT_EXIT,
    STATEMENT_ENTER,
    STATEMENT_EXIT,
    EXPRESSION_ENTER,
    EXPRESSION_EXIT,
    /**
     * Fired after a conditional construct (if / ternary / logical
     * short-circuit / switch case) decides which arm to take. The event
     * {@code value} is the {@link Boolean} outcome. Useful for coverage
     * and debugger tooling.
     */
    BRANCH,
    /**
     * Fired when a comparison operator evaluates. The event {@code value}
     * is an {@code Object[]} of {@code [lhs, operator, rhs]} with the
     * concrete operand values. Useful for coverage and debugger tooling.
     */
    COMPARE,
    /**
     * Fired when a computed (bracket) member is READ — {@code obj[key]}. The event
     * {@code value} is an {@code Object[]} of {@code [target, key, result]} with the
     * concrete object, the evaluated key, and the value returned. The read counterpart of
     * the {@code PROPERTY_SET} bind (writes ride {@code onBind}; reads, being observations
     * like {@link #BRANCH}/{@link #COMPARE}, ride {@code onEvent}). Scoped to bracket access —
     * dot reads ({@code obj.name}) do not fire — to stay cheap on hot paths while still
     * capturing dynamic/keyed lookups. Useful for coverage, data-flow tracing and debugging.
     */
    PROPERTY_GET

}
