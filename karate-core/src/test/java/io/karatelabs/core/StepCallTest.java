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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for call and callonce feature execution.
 */
class StepCallTest {

    @TempDir
    Path tempDir;

    @Test
    void testCallFeatureWithResult() throws Exception {
        // Create called feature
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called Feature
            Scenario: Return data
            * def result = { value: 42 }
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller Feature
            Scenario: Call another feature
            * def response = call read('called.feature')
            * match response.result == { value: 42 }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Suite should pass: " + getFailureMessage(result));
    }

    @Test
    void testCallFeatureWithArguments() throws Exception {
        // Create called feature that uses arguments
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called with Args
            Scenario: Use arguments
            * def doubled = inputValue * 2
            """);

        // Create caller feature
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller with Args
            Scenario: Pass arguments
            * def response = call read('called.feature') { inputValue: 21 }
            * match response.doubled == 42
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Suite should pass: " + getFailureMessage(result));
    }

    @Test
    void testCallonce() throws Exception {
        // Create a counter file to track calls
        Path counterFeature = tempDir.resolve("counter.feature");
        Files.writeString(counterFeature, """
            Feature: Counter
            Scenario: Increment
            * def callCount = typeof callCount == 'undefined' ? 1 : callCount + 1
            """);

        // Create feature that calls the counter twice with callonce
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Callonce Test
            Scenario: First scenario
            * def response = callonce read('counter.feature')

            Scenario: Second scenario
            * def response2 = callonce read('counter.feature')
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        // Both scenarios should pass
        assertTrue(result.isPassed());
        assertEquals(2, result.getScenarioCount());
    }

    @Test
    void testCallSimple() throws Exception {
        // Create a simple feature that sets variables
        Path calledFeature = tempDir.resolve("simple.feature");
        Files.writeString(calledFeature, """
            Feature: Simple
            Scenario: Set value
            * def x = 100
            """);

        // Create caller
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call Simple
            Scenario: Call and check
            * call read('simple.feature')
            * def a = 1
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed());
    }

    @Test
    void testNestedCall() throws Exception {
        // Create innermost feature
        Path inner = tempDir.resolve("inner.feature");
        Files.writeString(inner, """
            Feature: Inner
            Scenario: Set inner value
            * def innerValue = 'from-inner'
            """);

        // Create middle feature that calls inner
        Path middle = tempDir.resolve("middle.feature");
        Files.writeString(middle, """
            Feature: Middle
            Scenario: Call inner
            * def middleResponse = call read('inner.feature')
            * def middleValue = 'from-middle'
            """);

        // Create outer feature that calls middle
        Path outer = tempDir.resolve("outer.feature");
        Files.writeString(outer, """
            Feature: Outer
            Scenario: Call middle
            * def outerResponse = call read('middle.feature')
            * match outerResponse.middleValue == 'from-middle'
            """);

        SuiteResult result = runTestSuite(tempDir, outer.toString());

        assertTrue(result.isPassed(), "Nested calls should work: " + getFailureMessage(result));
    }

    @Test
    void testKarateCallWithArguments() throws Exception {
        // Create called feature that uses arguments
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario: Use arg
            * match foo == 'bar'
            """);

        // Create caller that uses karate.call() from JavaScript
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Call via JS
            * def foo = null
            * karate.call('called.feature', { foo: 'bar' })
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "karate.call() with args should work: " + getFailureMessage(result));
    }

    @Test
    void testKarateCallWithoutArguments() throws Exception {
        // Create called feature
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario: Simple
            * def x = 1
            """);

        // Create caller that uses karate.call() without arguments
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Call via JS
            * def result = karate.call('called.feature')
            * match result.x == 1
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "karate.call() without args should work: " + getFailureMessage(result));
    }

    @Test
    void testCallFeatureVariableWithArrayLoop() throws Exception {
        // Create called feature that uses 'foo' argument
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def res = foo
            """);

        // Create caller that reads feature into variable and calls with array
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario: Call with array loop
            * def called = read('called.feature')
            * def data = [{ foo: 'first' }, { foo: 'second' }]
            * def result = call called data
            * def extracted = karate.jsonPath(result, '$[*].res')
            * match extracted == ['first', 'second']
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Call with feature var and array loop should work: " + getFailureMessage(result));
    }

    @Test
    void testCallByTag() throws Exception {
        // Create called feature with multiple scenarios, each with a tag
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Tagged Scenarios
            @name=first
            Scenario: First
            * def bar = 1

            @name=second
            Scenario: Second
            * def bar = 2

            @name=third
            Scenario: Third
            * def bar = 3
            """);

        // Create caller that calls specific scenario by tag
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call by Tag
            Scenario: Call second scenario
            * def foo = call read('called.feature@name=second')
            * match foo.bar == 2
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Call by tag should work: " + getFailureMessage(result));
    }

    @Test
    void testCallByTagSameFile() throws Exception {
        // Create feature that calls a tagged scenario in the same file
        Path feature = tempDir.resolve("sameFile.feature");
        Files.writeString(feature, """
            Feature: Same File Tag Call

            Scenario: Caller
            * def foo = call read('@target')
            * match foo.bar == 42

            @ignore @target
            Scenario: Target
            * def bar = 42
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), "Same-file tag call should work: " + getFailureMessage(result));
    }

    @Test
    void testArgVariableInCalledFeature() throws Exception {
        // Tests that __arg is available in called feature
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * match __arg == { foo: 'bar' }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def params = { foo: 'bar' }
            * call read('called.feature') params
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "__arg should be available: " + getFailureMessage(result));
    }

    @Test
    void testArgVariableWithAssignment() throws Exception {
        // Tests that __arg is available even when call result is assigned to a variable
        // See https://github.com/karatelabs/karate/pull/1436
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * match __arg == { foo: 'bar' }
            * def result = 'done'
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def args = { foo: 'bar' }
            * def response = call read('called.feature') args
            * match response.result == 'done'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "__arg should be available with assignment: " + getFailureMessage(result));
    }

    @Test
    void testKarateCallWithSharedScope() throws Exception {
        // Tests karate.call(true, 'file') for shared scope
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def sharedVar = 'from-called'
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * karate.call(true, 'called.feature')
            * match sharedVar == 'from-called'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Shared scope call should merge variables: " + getFailureMessage(result));
    }

    @Test
    void testKarateCallIsolatedScope() throws Exception {
        // Tests that karate.call('file') is isolated - variable should NOT be in scope
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def isolatedVar = 'from-called'
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def result = karate.call('called.feature')
            * match result.isolatedVar == 'from-called'
            * match karate.get('isolatedVar') == null
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Isolated call should NOT merge variables: " + getFailureMessage(result));
    }

    @Test
    void testCallJsonPathOnParentVariable() throws Exception {
        // Tests that called feature can use JSONPath on variables from caller scope
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def vals = $foo[*].a
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def foo = [{a: 1}, {a: 2}]
            * def bar = call read('called.feature')
            * match bar.vals == [1, 2]
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "JSONPath on parent variable should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsFunction() throws Exception {
        // Tests calling a JS function returned by read('file.js')
        Path jsFile = tempDir.resolve("double.js");
        Files.writeString(jsFile, """
            function(x) {
              return x * 2;
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def result = call read('double.js') 5
            * match result == 10
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call read('file.js') should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsNamedFunction() throws Exception {
        // Tests calling a named JS function returned by read('file.js')
        Path jsFile = tempDir.resolve("helper.js");
        Files.writeString(jsFile, """
            function fn(x) {
              return { doubled: x * 2 };
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def result = call read('helper.js') 7
            * match result == { doubled: 14 }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call read('file.js') with named function should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsWithMultipleLines() throws Exception {
        // Tests calling a JS function with multiple lines and var declarations
        Path jsFile = tempDir.resolve("multi-line.js");
        Files.writeString(jsFile, """
            function fn(x) {
              var result = x + 1;
              return result;
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Multi-line JS function
            Scenario:
            * def result = call read('multi-line.js') 5
            * match result == 6
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Multi-line JS function should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsWithJavaType() throws Exception {
        // Tests calling a JS function that uses Java.type()
        Path jsFile = tempDir.resolve("java-type.js");
        Files.writeString(jsFile, """
            function fn() {
              var ArrayList = Java.type('java.util.ArrayList');
              var list = new ArrayList();
              list.add('hello');
              return list.size();
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: JS with Java.type
            Scenario:
            * def result = call read('java-type.js')
            * match result == 1
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "JS function with Java.type should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsWithJavaSort() throws Exception {
        // Tests calling a JS function that uses Java Collections.sort (V1 sort-array.feature pattern)
        Path jsFile = tempDir.resolve("sort-array.js");
        Files.writeString(jsFile, """
            function fn(array) {
              var ArrayList = Java.type('java.util.ArrayList');
              var Collections = Java.type('java.util.Collections');
              var list = new ArrayList();
              for (var i = 0; i < array.length; i++) {
                list.add(array[i]);
              }
              Collections.sort(list, java.lang.String.CASE_INSENSITIVE_ORDER);
              return list;
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Case insensitive sort
            Scenario:
            * def actual = ['C', 'b', 'A']
            * def sorted = call read('sort-array.js') actual
            * match sorted == ['A', 'b', 'C']
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "JS function with Java sort should work: " + getFailureMessage(result));
    }

    @Test
    void testCallFeatureWithEmbeddedExpression() throws Exception {
        // Tests that embedded expressions like #(variable) are properly evaluated
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def result = data
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def myData = { name: 'test', value: 42 }
            * def response = call read('called.feature') { data: '#(myData)' }
            * match response.result == { name: 'test', value: 42 }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Embedded expressions should be evaluated: " + getFailureMessage(result));
    }

    @Test
    void testCallReadJsWithSpacesInPath() throws Exception {
        // Tests that paths with spaces work correctly
        Path jsFile = tempDir.resolve("my function.js");
        Files.writeString(jsFile, """
            function fn(x) {
              return x * 3;
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def result = call read('my function.js') 7
            * match result == 21
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Path with spaces should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadWithVariable() throws Exception {
        // Tests call read(variable) where variable holds the path
        Path jsFile = tempDir.resolve("add.js");
        Files.writeString(jsFile, """
            function fn(x) {
              return x + 10;
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def path = 'add.js'
            * def result = call read(path) 5
            * match result == 15
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call read(variable) should work: " + getFailureMessage(result));
    }

    @Test
    void testCallReadFeatureWithVariable() throws Exception {
        // Tests call read(variable) where variable holds a feature path
        Path calledFeature = tempDir.resolve("target.feature");
        Files.writeString(calledFeature, """
            Feature: Target
            Scenario:
            * def output = input * 2
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Caller
            Scenario:
            * def path = 'target.feature'
            * def response = call read(path) { input: 21 }
            * match response.output == 42
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call read(variable) for feature should work: " + getFailureMessage(result));
    }

    @Test
    void testCallFunctionWithInlineJson() throws Exception {
        // Tests V1 syntax: call fun { key: 'value' } with inline JSON argument
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call with inline JSON
            Scenario:
            * def fun = function(arg){ return [arg.first, arg.second] }
            * def result = call fun { first: 'dummy', second: 'other' }
            * match result == ['dummy', 'other']
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call with inline JSON should work: " + getFailureMessage(result));
    }

    @Test
    void testCallFunctionWithEmbeddedExpressionInArg() throws Exception {
        // Tests V1 compatibility: embedded expressions like #(var) in function call arguments
        // should be evaluated before passing to the function.
        // This is used in demo/headers/headers.feature:75-82:
        //   * header Authorization = call fun { first: 'dummy', second: '#(token + time + demoBaseUrl)' }
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call with embedded expression in arg
            Scenario:
            * def token = 'abc'
            * def time = '123'
            * def url = 'http://test'
            * def fun = function(arg){ return [arg.first, arg.second] }
            * def result = call fun { first: 'dummy', second: '#(token + time + url)' }
            * match result == ['dummy', 'abc123http://test']
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Embedded expressions in function call args should be evaluated: " + getFailureMessage(result));
    }

    @Test
    void testCallInRhsOfHeader() throws Exception {
        // Tests V1 syntax: header X = call fun { key: 'value' }
        // The call expression in the RHS should be evaluated as Karate expression, not pure JS
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call in header RHS
            Scenario:
            * def fun = function(arg){ return arg.first + '-' + arg.second }
            * def expected = call fun { first: 'hello', second: 'world' }
            * match expected == 'hello-world'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call in expression RHS should work: " + getFailureMessage(result));
    }

    @Test
    void testCallLoopWithReadFeature() throws Exception {
        // Tests V1 syntax: call read('feature') array
        // Each element in array should be passed to the called feature
        // __loop should contain the iteration index (0, 1, 2, ...)
        // __arg should contain the current element
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def loopIndex = __loop
            * def argValue = __arg
            * def nameValue = name
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call Loop
            Scenario:
            * def data = [{ name: 'Bob' }, { name: 'Alice' }, { name: 'Eve' }]
            * def result = call read('called.feature') data
            * match result[0].loopIndex == 0
            * match result[1].loopIndex == 1
            * match result[2].loopIndex == 2
            * match result[0].nameValue == 'Bob'
            * match result[1].nameValue == 'Alice'
            * match result[2].nameValue == 'Eve'
            * match result[0].argValue == { name: 'Bob' }
            * match result[1].argValue == { name: 'Alice' }
            * match result[2].argValue == { name: 'Eve' }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "call loop with __loop and __arg should work: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceLoopWithReadFeature() throws Exception {
        // Tests V1 syntax: callonce read('feature') array
        // Result should be cached and returned on subsequent calls
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * def result = name + '-' + __loop
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Callonce Loop
            Background:
            * def data = [{ name: 'A' }, { name: 'B' }]
            * def result = callonce read('called.feature') data

            Scenario: First
            * match result[0].result == 'A-0'
            * match result[1].result == 'B-1'

            Scenario: Second
            * match result[0].result == 'A-0'
            * match result[1].result == 'B-1'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "callonce loop should work: " + getFailureMessage(result));
    }

    @Test
    void testCallLoopAccessParentVariable() throws Exception {
        // Tests that loop call can access parent variables via __arg reference
        // V1 pattern: match __arg == parentVar[__loop]
        Path calledFeature = tempDir.resolve("called.feature");
        Files.writeString(calledFeature, """
            Feature: Called
            Scenario:
            * match __arg == data[__loop]
            * def processed = name + '-processed'
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Loop with parent access
            Scenario:
            * def data = [{ name: 'first' }, { name: 'second' }]
            * def result = call read('called.feature') data
            * match result[*].processed == ['first-processed', 'second-processed']
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "loop call should access parent variables: " + getFailureMessage(result));
    }

    @Test
    void testCallUpdatesConfigSharedScope() throws Exception {
        // V1 compatibility: When using shared scope (callonce without assignment),
        // configure statements in the called feature should affect the caller's config.
        // This is the pattern used in call-updates-config.feature.
        Path calledFeature = tempDir.resolve("common.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Common routine that updates config
            Scenario:
            * def token = 'my-auth-token'
            * configure headers = { 'Authorization': '#(token)' }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call updates config
            Background:
            * callonce read('common.feature')

            Scenario: Config should be updated by called feature
            * match karate.config.headers != null
            * match karate.config.headers.Authorization == 'my-auth-token'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Shared scope call should propagate config: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceUpdatesConfigCached() throws Exception {
        // V1 compatibility: callonce should cache and restore config changes
        // When a second scenario uses the same callonce, it should get the same config
        Path calledFeature = tempDir.resolve("setup.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Setup that configures headers
            Scenario:
            * def baseUrl = 'http://example.com'
            * configure headers = { 'X-Custom': 'value' }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Callonce config caching
            Background:
            * callonce read('setup.feature')

            Scenario: First scenario gets config
            * match karate.config.headers == { 'X-Custom': 'value' }

            Scenario: Second scenario also gets cached config
            * match karate.config.headers == { 'X-Custom': 'value' }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertEquals(2, result.getScenarioCount());
        assertTrue(result.isPassed(), "Callonce should cache and restore config: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceSharedScopeWithHeadersFunction() throws Exception {
        // V1 compatibility: When using shared scope, both variables and config should propagate.
        // This tests the pattern from karate-demo where headers.js uses karate.get() to access variables.
        Path headersJs = tempDir.resolve("headers.js");
        Files.writeString(headersJs, """
            function fn() {
              var token = karate.get('token');
              var time = karate.get('time');
              if (token && time) {
                return { 'Authorization': token + '-' + time };
              } else {
                return {};
              }
            }
            """);

        Path calledFeature = tempDir.resolve("common.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Common routine that sets variables and headers function
            Scenario:
            * def token = 'my-token'
            * def time = 'my-time'
            * configure headers = read('headers.js')
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Shared scope with headers function
            Background:
            # Shared scope - config AND variables should propagate
            * callonce read('common.feature')

            Scenario: Variables and headers function should be available
            # Variables should be accessible
            * match token == 'my-token'
            * match time == 'my-time'
            # Headers function should return correct value using the variables
            * def headersResult = karate.config.headers()
            * match headersResult == { 'Authorization': 'my-token-my-time' }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Shared scope should propagate both vars and headers function: " + getFailureMessage(result));
    }

    @Test
    void testCallSharedScopePropagatesCookieJar() throws Exception {
        // V1 compatibility: When using shared scope, cookies collected from HTTP responses
        // in the called feature should be available in the caller's cookie jar.
        // This is the pattern used in karate-demo's common.feature where responseCookies
        // from a login request should be auto-sent on subsequent requests.
        //
        // We test this by using configure cookies in the called feature, which updates
        // the cookie jar, and verifying it's accessible in the caller.
        Path calledFeature = tempDir.resolve("common.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Common routine that sets cookies
            Scenario:
            # Set a cookie via configure - this updates the cookie jar
            * configure cookies = { session: 'abc123' }
            * def setupDone = true
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Shared scope should propagate cookie jar
            Scenario: Cookie config should propagate from called feature
            # Shared scope - cookies should propagate
            * call read('common.feature')
            # Verify variable propagated
            * match setupDone == true
            # Verify cookie config propagated
            * def cookies = karate.config.cookies
            * match cookies == { session: 'abc123' }
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Shared scope should propagate cookies config: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceIsolatedScopeDoesNotPropagateConfig() throws Exception {
        // V1 compatibility: When using isolated scope (def x = callonce read(...)),
        // configure statements in the called feature should NOT affect the caller's config.
        // This is the pattern used in call-isolated-config.feature.
        Path calledFeature = tempDir.resolve("common.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Common routine that updates config
            Scenario:
            * def token = 'my-auth-token'
            * def time = 'my-time'
            * configure headers = { 'Authorization': '#(token)' }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call with isolated scope does not update config
            Background:
            # Using assignment - should NOT propagate config
            * def setup = callonce read('common.feature')

            Scenario: Config should NOT be updated by called feature (isolated scope)
            # Use intermediate variable to avoid match expression evaluation issue
            * def actualHeaders = karate.config.headers
            * match actualHeaders == null

            Scenario: But the returned variables should be accessible
            * match setup.token == 'my-auth-token'
            * match setup.time == 'my-time'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertEquals(2, result.getScenarioCount());
        assertTrue(result.isPassed(), "Isolated scope call should NOT propagate config: " + getFailureMessage(result));
    }

    @Test
    void testCallIsolatedScopeDoesNotPropagateConfig() throws Exception {
        // Same as above but with regular call (not callonce)
        Path calledFeature = tempDir.resolve("common.feature");
        Files.writeString(calledFeature, """
            @ignore
            Feature: Common routine that updates config
            Scenario:
            * def myVar = 'value'
            * configure headers = { 'X-Test': 'from-called' }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Call with isolated scope does not update config
            Scenario: Config should NOT be updated by called feature (isolated scope)
            # Using assignment - should NOT propagate config
            * def result = call read('common.feature')
            # Use intermediate variable to avoid match expression evaluation issue
            * def actualHeaders = karate.config.headers
            * match actualHeaders == null
            * match result.myVar == 'value'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Isolated scope call should NOT propagate config: " + getFailureMessage(result));
    }

    @Test
    void testCallOnceWithFunctionInResult() throws Exception {
        // Tests that functions in callonce results are preserved and not converted to Maps.
        // JsFunction extends JsObject which implements Map, so deepCopy must check
        // for JsCallable before checking for Map to avoid mangling functions.
        Path jsFile = tempDir.resolve("utils.js");
        Files.writeString(jsFile, """
            function fn() {
              return {
                value: 42,
                getValue: function() { return 42; },
                process: function(x) { return x * 2; }
              };
            }
            """);

        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Callonce with function in result
            Background:
            * callonce read('utils.js')
            Scenario: First scenario - uses cached result
            * def result = getValue()
            * match result == 42
            * match process(5) == 10
            Scenario: Second scenario - also uses cached result
            * def result = getValue()
            * match result == 42
            * match process(10) == 20
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Functions in callonce results should be callable: " + getFailureMessage(result));
    }

    @Test
    void testDefWithFunctionInObjectLiteral() throws Exception {
        // Tests that functions inside object literals are preserved when using def.
        // This catches regressions in processEmbeddedExpressions which must check
        // for JsCallable before checking for Map.
        Path callerFeature = tempDir.resolve("caller.feature");
        Files.writeString(callerFeature, """
            Feature: Function in object literal
            Scenario: Access function from object
            * def foo = { bar: function(){ return 'baz' } }
            * def result = foo.bar()
            * match result == 'baz'
            Scenario: Chained access
            * def foo = { bar: { baz: function(){ return 'deep' } } }
            * def result = foo.bar.baz()
            * match result == 'deep'
            """);

        SuiteResult result = runTestSuite(tempDir, callerFeature.toString());

        assertTrue(result.isPassed(), "Functions in object literals should be callable: " + getFailureMessage(result));
    }

    private String getFailureMessage(SuiteResult result) {
        if (result.isPassed()) return "none";
        for (FeatureResult fr : result.getFeatureResults()) {
            for (ScenarioResult sr : fr.getScenarioResults()) {
                if (sr.isFailed()) {
                    return sr.getFailureMessage();
                }
            }
        }
        return "unknown";
    }

}
