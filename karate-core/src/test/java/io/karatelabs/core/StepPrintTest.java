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

import org.junit.jupiter.api.Test;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class StepPrintTest {

    @Test
    void testPrintString() {
        ScenarioRuntime sr = run("""
            * print 'hello world'
            """);
        assertPassed(sr);
        assertLogContains(sr, "hello world");
    }

    @Test
    void testPrintVariable() {
        ScenarioRuntime sr = run("""
            * def name = 'test'
            * print name
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("test"), "expected 'test' in: " + log);
    }

    @Test
    void testPrintJson() {
        ScenarioRuntime sr = run("""
            * def data = { name: 'john', age: 30 }
            * print data
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("john") && log.contains("name"));
    }

    @Test
    void testPrintExpression() {
        ScenarioRuntime sr = run("""
            * def x = 10
            * print 'x squared is:', x * x
            """);
        assertPassed(sr);
        assertLogContains(sr, "x squared is:");
        assertLogContains(sr, "100");
    }

    @Test
    void testPrintMultipleLiterals() {
        ScenarioRuntime sr = run("""
            * print 'foo', 'bar'
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 0);
        assertTrue(log.contains("foo"), "expected 'foo' in: " + log);
        assertTrue(log.contains("bar"), "expected 'bar' in: " + log);
    }

    @Test
    void testPrintXml() {
        ScenarioRuntime sr = run("""
            * def foo = <bar><baz>1</baz></bar>
            * print foo
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("bar") && log.contains("baz"));
    }

    @Test
    void testPrintWithCommasInString() {
        ScenarioRuntime sr = run("""
            * def foo = { bar: 1 }
            * print 'the value, of foo, is:', foo
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("the value, of foo, is:"));
    }

    @Test
    void testPrintMultiExpression() {
        ScenarioRuntime sr = run("""
            * def foo = 'bar'
            * print foo, 1
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("bar"), "expected 'bar' in: " + log);
        assertTrue(log.contains("1"), "expected '1' in: " + log);
    }

    @Test
    void testPrintJsonMultiExpression() {
        ScenarioRuntime sr = run("""
            * def foo = { bar: 1 }
            * print foo, foo.bar
            """);
        assertPassed(sr);
        String log = getStepLog(sr, 1);
        assertTrue(log.contains("bar"), "expected 'bar' in: " + log);
    }

}
