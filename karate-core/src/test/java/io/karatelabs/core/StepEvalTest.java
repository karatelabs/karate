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

/**
 * Tests for line-level JS evaluation in Gherkin steps.
 * Covers edge cases where the step line is evaluated as a JS expression.
 *
 * Key scenarios:
 * - Function calls as step lines: * myFunc()
 * - JS expressions with punctuation: * foo.bar, * foo['key']
 * - Keywords followed by expressions that look like function calls: * eval (1 + 2)
 */
class StepEvalTest {

    // ========== Function Call as Step Line ==========

    @Test
    void testFunctionCallNoArgs() {
        // * foo() - keyword is "foo", text is "()"
        ScenarioRuntime sr = run("""
            * def result = null
            * def foo = function(){ result = 'called' }
            * foo()
            * match result == 'called'
            """);
        assertPassed(sr);
    }

    @Test
    void testFunctionCallWithArgs() {
        // * myFunc('arg') - keyword is "myFunc", text is "('arg')"
        ScenarioRuntime sr = run("""
            * def result = null
            * def myFunc = function(x){ result = x }
            * myFunc('hello')
            * match result == 'hello'
            """);
        assertPassed(sr);
    }

    @Test
    void testFunctionCallWithSpaceBeforeParen() {
        // * myFunc ('arg') - keyword is "myFunc", text is " ('arg')"
        ScenarioRuntime sr = run("""
            * def result = null
            * def myFunc = function(x){ result = x }
            * myFunc ('world')
            * match result == 'world'
            """);
        assertPassed(sr);
    }

    @Test
    void testFunctionCallMultipleArgs() {
        ScenarioRuntime sr = run("""
            * def result = null
            * def add = function(a, b){ result = a + b }
            * add(2, 3)
            * match result == 5
            """);
        assertPassed(sr);
    }

    // ========== Eval Keyword with Expressions ==========

    @Test
    void testEvalKeywordWithParenthesizedExpression() {
        // * eval (1 + 2) - must NOT be interpreted as "eval(1 + 2)"
        // The "eval" keyword should be recognized, and "(1 + 2)" is the expression
        ScenarioRuntime sr = run("""
            * def result = null
            * eval result = (1 + 2)
            * match result == 3
            """);
        assertPassed(sr);
    }

    @Test
    void testEvalKeywordWithFunctionCall() {
        // * eval foo() - eval keyword with function call expression
        ScenarioRuntime sr = run("""
            * def result = null
            * def foo = function(){ return 42 }
            * eval result = foo()
            * match result == 42
            """);
        assertPassed(sr);
    }

    @Test
    void testEvalKeywordSimpleExpression() {
        ScenarioRuntime sr = run("""
            * def x = 10
            * eval x = x + 5
            * match x == 15
            """);
        assertPassed(sr);
    }

    @Test
    void testEvalKeywordWithDocString() {
        ScenarioRuntime sr = run("""
            * def result = null
            * eval
              \"\"\"
              result = 1 + 2 + 3
              \"\"\"
            * match result == 6
            """);
        assertPassed(sr);
    }

    // ========== JS Expressions with Punctuation ==========

    @Test
    void testDotNotationExpression() {
        // * foo.bar() - keyword "foo.bar" has punctuation, treated as JS
        ScenarioRuntime sr = run("""
            * def foo = { bar: function(){ return 'baz' } }
            * def result = foo.bar()
            * match result == 'baz'
            """);
        assertPassed(sr);
    }

    @Test
    void testBracketNotationExpression() {
        // * foo['bar'] - keyword has punctuation, treated as JS
        ScenarioRuntime sr = run("""
            * def foo = { bar: 'value' }
            * def result = foo['bar']
            * match result == 'value'
            """);
        assertPassed(sr);
    }

    @Test
    void testChainedExpression() {
        // * foo.bar.baz() - chained method call, keyword is "foo.bar.baz"
        ScenarioRuntime sr = run("""
            * def foo = { bar: { baz: function(){ return 'deep' } } }
            * def result = foo.bar.baz()
            * match result == 'deep'
            """);
        assertPassed(sr);
    }

    @Test
    void testMixedBracketAndDot() {
        // * foo['bar'].baz - mixed bracket and dot notation
        ScenarioRuntime sr = run("""
            * def foo = { bar: { baz: 'mixed' } }
            * def result = foo['bar'].baz
            * match result == 'mixed'
            """);
        assertPassed(sr);
    }

    @Test
    void testJsAssignmentAsSideEffect() {
        // Direct JS assignment works when keyword has punctuation
        ScenarioRuntime sr = run("""
            * def obj = { value: 0 }
            * obj.value = 42
            * match obj.value == 42
            """);
        assertPassed(sr);
    }

    // ========== Plain Expressions (no keyword) ==========

    @Test
    void testPlainPrintExpression() {
        // * print foo - no keyword detected, text is "print foo"
        ScenarioRuntime sr = run("""
            * def foo = 'hello'
            * print foo
            """);
        assertPassed(sr);
    }

    @Test
    void testPlainAssertExpression() {
        ScenarioRuntime sr = run("""
            * def x = 5
            * assert x == 5
            """);
        assertPassed(sr);
    }

    // ========== Edge Cases ==========

    @Test
    void testUnknownKeywordThrows() {
        // * unknownKeyword someValue - should throw "unknown keyword"
        ScenarioRuntime sr = run("""
            * unknownKeyword someValue
            """);
        assertFailedWith(sr, "unknown keyword: unknownKeyword");
    }

    @Test
    void testFunctionCallWithComplexArgs() {
        ScenarioRuntime sr = run("""
            * def result = null
            * def process = function(obj){ result = obj.name }
            * process({ name: 'test', value: 123 })
            * match result == 'test'
            """);
        assertPassed(sr);
    }

    @Test
    void testFunctionCallWithArrayArg() {
        ScenarioRuntime sr = run("""
            * def result = null
            * def sumAll = function(arr){ var sum = 0; arr.forEach(function(x){ sum += x }); result = sum }
            * sumAll([1, 2, 3, 4])
            * match result == 10
            """);
        assertPassed(sr);
    }

    @Test
    void testFunctionCallReturningValue() {
        // Function call result can be assigned
        ScenarioRuntime sr = run("""
            * def add = function(a, b){ return a + b }
            * def result = add(10, 20)
            * match result == 30
            """);
        assertPassed(sr);
    }

    @Test
    void testNestedFunctionCalls() {
        ScenarioRuntime sr = run("""
            * def double = function(x){ return x * 2 }
            * def addOne = function(x){ return x + 1 }
            * def result = double(addOne(5))
            * match result == 12
            """);
        assertPassed(sr);
    }

    @Test
    void testIIFE() {
        // Immediately Invoked Function Expression
        ScenarioRuntime sr = run("""
            * def result = (function(){ return 'iife' })()
            * match result == 'iife'
            """);
        assertPassed(sr);
    }

    @Test
    void testArrowFunctionCall() {
        ScenarioRuntime sr = run("""
            * def greet = (name) => 'Hello ' + name
            * def result = greet('World')
            * match result == 'Hello World'
            """);
        assertPassed(sr);
    }

    // ========== Distinguishing Keywords from Function Names ==========

    @Test
    void testDefKeywordNotConfusedWithFunction() {
        // "def" is a keyword, not a function call
        ScenarioRuntime sr = run("""
            * def x = 5
            * match x == 5
            """);
        assertPassed(sr);
    }

    @Test
    void testPrintKeywordNotConfusedWithFunction() {
        // "print" with space is keyword, not print()
        ScenarioRuntime sr = run("""
            * def msg = 'test'
            * print msg
            """);
        assertPassed(sr);
    }

    @Test
    void testMatchKeywordNotConfusedWithFunction() {
        // "match" is keyword
        ScenarioRuntime sr = run("""
            * def foo = { a: 1 }
            * match foo == { a: 1 }
            """);
        assertPassed(sr);
    }

}
