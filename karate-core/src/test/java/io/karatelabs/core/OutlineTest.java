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

import io.karatelabs.common.Resource;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Scenario Outline expansion and execution.
 * Includes both static Examples tables and dynamic outlines via @setup scenarios.
 */
public class OutlineTest {

    @TempDir
    Path tempDir;

    // ========== Static Outline Tests ==========

    @Test
    void testBasicOutlineExpansion() throws Exception {
        Path feature = tempDir.resolve("outline-basic.feature");
        Files.writeString(feature, """
            Feature: Basic Outline

            Scenario Outline: Test with <name>
            * def value = '<name>'
            * match value == '<name>'

            Examples:
            | name  |
            | alice |
            | bob   |
            | carol |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
        assertEquals(3, result.getScenarioPassedCount());
    }

    @Test
    void testOutlineWithMultipleColumns() throws Exception {
        Path feature = tempDir.resolve("outline-multi.feature");
        Files.writeString(feature, """
            Feature: Multi-column Outline

            Scenario Outline: Add <a> + <b> = <sum>
            * def result = <a> + <b>
            * match result == <sum>

            Examples:
            | a! | b! | sum! |
            | 1  | 2  | 3    |
            | 5  | 5  | 10   |
            | 0  | 0  | 0    |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
    }

    @Test
    void testOutlineWithStringSubstitution() throws Exception {
        Path feature = tempDir.resolve("outline-string.feature");
        Files.writeString(feature, """
            Feature: String Substitution

            Scenario Outline: Greeting <person>
            * def greeting = 'Hello <person>!'
            * match greeting == '<expected>'

            Examples:
            | person | expected      |
            | World  | Hello World!  |
            | Karate | Hello Karate! |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testOutlineWithJsonSubstitution() throws Exception {
        Path feature = tempDir.resolve("outline-json.feature");
        Files.writeString(feature, """
            Feature: JSON in Outline

            Scenario Outline: User <name> is <age> years old
            * def user = { name: '<name>', age: <age> }
            * match user.name == '<name>'
            * match user.age == <age>

            Examples:
            | name  | age! |
            | john  | 30   |
            | jane  | 25   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testMultipleExamplesTables() throws Exception {
        Path feature = tempDir.resolve("outline-multi-tables.feature");
        Files.writeString(feature, """
            Feature: Multiple Examples Tables

            Scenario Outline: Test <type> with value <val>
            * def x = <val>
            * match x == <val>

            Examples: Positive values
            | type     | val! |
            | positive | 1    |
            | positive | 10   |

            Examples: Zero and negative
            | type     | val! |
            | zero     | 0    |
            | negative | -5   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        // 2 rows from first table + 2 rows from second table = 4 scenarios
        assertEquals(4, result.getScenarioCount());
    }

    @Test
    void testOutlineWithBackground() throws Exception {
        Path feature = tempDir.resolve("outline-background.feature");
        Files.writeString(feature, """
            Feature: Outline with Background

            Background:
            * def base = 100

            Scenario Outline: Add <val> to base
            * def result = base + <val>
            * match result == <expected>

            Examples:
            | val! | expected! |
            | 1    | 101       |
            | 50   | 150       |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioCount());
    }

    @Test
    void testOutlineFailure() throws Exception {
        Path feature = tempDir.resolve("outline-fail.feature");
        Files.writeString(feature, """
            Feature: Outline with Failure

            Scenario Outline: Test <val>
            * def actual = <val>
            * match actual == <expected>

            Examples:
            | val! | expected! |
            | 1    | 1         |
            | 2    | 999       |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertFalse(result.isPassed());
        assertEquals(2, result.getScenarioCount());
        assertEquals(1, result.getScenarioPassedCount());
        assertEquals(1, result.getScenarioFailedCount());
    }

    @Test
    void testOutlineWithStringConcatenation() throws Exception {
        // Simpler test that avoids docstring parsing complexity
        Path feature = tempDir.resolve("outline-concat.feature");
        Files.writeString(feature, """
            Feature: Outline with String Concat

            Scenario Outline: Template <id>
            * def body = 'Hello ' + name + '!'
            * match body == 'Hello <name>!'

            Examples:
            | id  | name  |
            | 001 | alice |
            | 002 | bob   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioCount());
    }

    @Test
    void testOutlineWithJsonObject() throws Exception {
        // Simpler test that avoids table parsing complexity
        Path feature = tempDir.resolve("outline-json2.feature");
        Files.writeString(feature, """
            Feature: Outline with JSON

            Scenario Outline: JSON test <id>
            * def data = { id: id, val: val }
            * match data.id == '<id>'
            * match data.val == <val>

            Examples:
            | id | val! |
            | a  | 1    |
            | b  | 2    |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioCount());
    }

    @Test
    void testOutlineExampleVariablesAccessible() throws Exception {
        Path feature = tempDir.resolve("outline-vars.feature");
        Files.writeString(feature, """
            Feature: Outline Variables

            Scenario Outline: Direct access to example vars
            * def computed = name + '-' + value
            * match computed == '<name>-<value>'

            Examples:
            | name | value |
            | foo  | bar   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testOutlineScenarioNameSubstituted() throws Exception {
        // Verify karate.scenario.name and karate.info.scenarioName reflect substituted values
        Path feature = tempDir.resolve("outline-name.feature");
        Files.writeString(feature, """
            Feature: Outline Name Substitution

            Scenario Outline: name is <name> and age is <age>
            * def expected = 'name is ' + name + ' and age is ' + age
            * match karate.scenario.name == expected
            * match karate.info.scenarioName == expected

            Examples:
            | name | age! |
            | Bob  | 5    |
            | Nyan | 6    |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testOutlinePlaceholderInDocString() throws Exception {
        // Verify placeholders are substituted in docstrings
        Path feature = tempDir.resolve("outline-docstring.feature");
        Files.writeString(feature, """
            Feature: Outline DocString

            Scenario Outline: Test docstring with <name>
            * text expected =
            \"\"\"
            Hello <name>!
            \"\"\"
            * match expected contains name

            Examples:
            | name   |
            | World  |
            | Karate |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testOutlinePlaceholderInStepTable() throws Exception {
        // Verify placeholders are substituted in step tables
        // Note: table keyword evaluates cells as expressions, so use quoted values
        Path feature = tempDir.resolve("outline-table.feature");
        Files.writeString(feature, """
            Feature: Outline Step Table

            Scenario Outline: Test table with <name>
            * table data
            | name   | value |
            | '<name>' | '<value>' |
            * match data[0].name == name
            * match data[0].value == value

            Examples:
            | name | value |
            | foo  | one   |
            | bar  | two   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(2, result.getScenarioPassedCount());
    }

    @Test
    void testMixedScenarioAndOutline() throws Exception {
        Path feature = tempDir.resolve("mixed.feature");
        Files.writeString(feature, """
            Feature: Mixed Scenarios

            Scenario: Regular scenario
            * def x = 1
            * match x == 1

            Scenario Outline: Outline <val>
            * def y = <val>
            * match y == <val>

            Examples:
            | val! |
            | 2    |
            | 3    |

            Scenario: Another regular scenario
            * def z = 4
            * match z == 4
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        // 1 regular + 2 from outline + 1 regular = 4 scenarios
        assertEquals(4, result.getScenarioCount());
    }

    @Test
    void testTypeHints() throws Exception {
        // Columns ending with ! are evaluated as JS expressions
        Path feature = tempDir.resolve("type-hints.feature");
        Files.writeString(feature, """
            Feature: Type Hints

            Scenario Outline: Type hint <description>
            * match value == expected

            Examples:
            | description       | value!           | expected!              |
            | number            | 42               | 42                     |
            | boolean true      | true             | true                   |
            | boolean false     | false            | false                  |
            | null              | null             | null                   |
            | array             | [1, 2, 3]        | [1, 2, 3]              |
            | object            | { a: 1 }         | { a: 1 }               |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(6, result.getScenarioCount());
    }

    @Test
    void testStringColumnsWithoutTypeHint() throws Exception {
        // Columns without ! are treated as strings
        Path feature = tempDir.resolve("string-columns.feature");
        Files.writeString(feature, """
            Feature: String Columns

            Scenario Outline: String value <val>
            * match val == '<val>'
            * match typeof val == 'string'

            Examples:
            | val |
            | 123 |
            | abc |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
    }

    @Test
    void testEmptyCellsWithTypeHintBecomeNull() throws Exception {
        // V1 compatibility: empty cells with type hint (!) should become null
        Path feature = tempDir.resolve("empty-typehint.feature");
        Files.writeString(feature, """
            Feature: Empty Cells with Type Hint

            Scenario Outline: Test with value
            * match value == expected

            Examples:
            | value! | expected! |
            | 42     | 42        |
            |        | null      |
            | true   | true      |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
    }

    @Test
    void testEmptyCellsInParamsSkipped() throws Exception {
        // V1 compatibility: empty cells with type hint should be null
        Path feature = tempDir.resolve("empty-params.feature");
        Files.writeString(feature, """
            Feature: Empty Cells in Params

            Scenario Outline: Search test
            # Verify nulls are set correctly for empty cells with type hints
            * match name == expectedName
            * match country == expectedCountry
            * match limit == expectedLimit

            Examples:
            | name | country | limit! | expectedName! | expectedCountry! | expectedLimit! |
            | foo  | IN      |      5 | 'foo'         | 'IN'             | 5              |
            | bar  |         |     10 | 'bar'         | null             | 10             |
            | baz  |         |        | 'baz'         | null             | null           |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
    }

    // ========== Dynamic Outline Tests ==========

    @Test
    void testDynamicOutlineWithSetup() throws Exception {
        Path feature = tempDir.resolve("dynamic-setup.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Setup

            @setup
            Scenario: Generate test data
            * def data = [{a: 1, expected: 1}, {a: 2, expected: 2}]

            Scenario Outline: Test <a>
            * def actual = <a>
            * match actual == <expected>

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(2, result.getPassedCount(), "Should have 2 passing scenarios from dynamic data");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithSetupOnce() throws Exception {
        Path feature = tempDir.resolve("dynamic-setuponce.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with SetupOnce

            @setup
            Scenario: Generate test data
            * def data = [{x: 10}, {x: 20}, {x: 30}]

            Scenario Outline: First outline <x>
            * def result = <x> * 2
            * match result == <x> * 2

            Examples:
            | karate.setupOnce().data |

            Scenario Outline: Second outline <x>
            * def result = <x> + 5
            * match result == <x> + 5

            Examples:
            | karate.setupOnce().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Both outlines use the same cached data (3 items each = 6 total scenarios)
        assertEquals(6, result.getPassedCount(), "Should have 6 passing scenarios (3+3 from cached setup)");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithInlineExpression() throws Exception {
        Path feature = tempDir.resolve("dynamic-inline.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Inline Expression

            Scenario Outline: Test inline data <name>
            * def greeting = 'Hello, ' + '<name>'
            * match greeting == 'Hello, ' + '<name>'

            Examples:
            | [{name: 'Alice'}, {name: 'Bob'}, {name: 'Charlie'}] |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(3, result.getPassedCount(), "Should have 3 passing scenarios from inline array");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithFailure() throws Exception {
        Path feature = tempDir.resolve("dynamic-failure.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Failure

            @setup
            Scenario: Generate test data
            * def data = [{val: 1, exp: 1}, {val: 2, exp: 999}]

            Scenario Outline: Test value <val>
            * match <val> == <exp>

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(1, result.getPassedCount(), "First scenario should pass (1 == 1)");
        assertEquals(1, result.getFailedCount(), "Second scenario should fail (2 != 999)");
    }

    @Test
    void testSetupScenarioNotExecutedDirectly() throws Exception {
        Path feature = tempDir.resolve("setup-not-direct.feature");
        Files.writeString(feature, """
            Feature: Setup Scenario Not Executed Directly

            @setup
            Scenario: This should not run directly
            * def data = [{val: 1}]
            * def setupRan = true

            Scenario: Regular scenario
            * def regular = true
            * match regular == true
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Only the regular scenario should run, not the @setup scenario
        assertEquals(1, result.getPassedCount(), "Only regular scenario should run");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithNamedSetup() throws Exception {
        Path feature = tempDir.resolve("dynamic-named-setup.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Named Setup

            @setup=users
            Scenario: Generate user data
            * def data = [{name: 'user1'}, {name: 'user2'}]

            @setup=products
            Scenario: Generate product data
            * def data = [{name: 'product1'}, {name: 'product2'}, {name: 'product3'}]

            Scenario Outline: Test user <name>
            * match '<name>' contains 'user'

            Examples:
            | karate.setup('users').data |

            Scenario Outline: Test product <name>
            * match '<name>' contains 'product'

            Examples:
            | karate.setup('products').data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // 2 user scenarios + 3 product scenarios = 5 total
        assertEquals(5, result.getPassedCount(), "Should have 5 passing scenarios (2 users + 3 products)");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithBackground() throws Exception {
        Path feature = tempDir.resolve("dynamic-background.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Background

            Background:
            * def baseValue = 100

            @setup
            Scenario: Generate test data
            * def data = [{multiplier: 1}, {multiplier: 2}]

            Scenario Outline: Test multiplier <multiplier>
            * def result = baseValue * <multiplier>
            * match result == 100 * <multiplier>

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(2, result.getPassedCount(), "Background should run before each dynamic scenario");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineEmptyData() throws Exception {
        Path feature = tempDir.resolve("dynamic-empty.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Empty Data

            @setup
            Scenario: Generate empty data
            * def data = []

            Scenario Outline: This should not run
            * def val = <a>

            Examples:
            | karate.setup().data |

            Scenario: Regular scenario after empty outline
            * def ran = true
            * match ran == true
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Empty data means no outline scenarios run, but regular scenario should
        assertEquals(1, result.getPassedCount(), "Only regular scenario should run");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithComplexData() throws Exception {
        Path feature = tempDir.resolve("dynamic-complex.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Complex Data

            @setup
            Scenario: Generate complex test data
            * def data =
              \"\"\"
              [
                { id: 1, name: 'item1', tags: ['a', 'b'] },
                { id: 2, name: 'item2', tags: ['c'] }
              ]
              \"\"\"

            Scenario Outline: Test item <id>
            * match <id> == <id>
            * match '<name>' == '<name>'

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(2, result.getPassedCount(), "Should handle complex nested data");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testMissingSetupScenario() throws Exception {
        Path feature = tempDir.resolve("missing-setup.feature");
        Files.writeString(feature, """
            Feature: Missing Setup Scenario

            Scenario Outline: Test <val>
            * def x = <val>

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);

        // Should fail gracefully (not crash) when @setup scenario is missing
        FeatureResult result = fr.call();
        assertTrue(result.isFailed(), "Should fail when @setup scenario is missing");
        assertTrue(result.getFailureMessage().contains("setup"), "Error message should mention @setup");
    }

    // ========== Generator Function Tests ==========

    @Test
    void testDynamicOutlineWithGeneratorFunction() throws Exception {
        Path feature = tempDir.resolve("dynamic-generator.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Generator Function

            @setup
            Scenario: Define generator
            * def generator = function(i){ if (i == 3) return null; return { name: 'item' + i, index: i } }

            Scenario Outline: Test generated item <name>
            * match '<name>' == 'item' + <index>
            * match __num == <index>

            Examples:
            | karate.setup().generator |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Generator returns items at index 0, 1, 2 (stops at 3 with null)
        assertEquals(3, result.getPassedCount(), "Should have 3 passing scenarios from generator function");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithGeneratorReturningNonMap() throws Exception {
        Path feature = tempDir.resolve("dynamic-generator-nonmap.feature");
        Files.writeString(feature, """
            Feature: Dynamic Outline with Generator Returning Non-Map

            @setup
            Scenario: Define generator that returns false to stop
            * def generator = function(i){ if (i >= 2) return false; return { val: i * 10 } }

            Scenario Outline: Test value <val>
            * match <val> == __num * 10

            Examples:
            | karate.setup().generator |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Generator returns items at index 0, 1 (stops at 2 with false)
        assertEquals(2, result.getPassedCount(), "Should have 2 passing scenarios (stops when non-map returned)");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineGeneratorMatchingV1Syntax() throws Exception {
        // This test matches the v1 outline-generator.feature example
        Path feature = tempDir.resolve("dynamic-generator-v1.feature");
        Files.writeString(feature, """
            Feature: Generator Function (v1 Compatible)

            @setup
            Scenario: Setup generator
            * def generator = function(i){ if (i == 5) return null; return { name: 'cat' + i, age: i } }

            Scenario Outline: Test generated cat
            * match __num == age
            * match __row.name == 'cat' + age

            Examples:
            | karate.setup().generator |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Generator creates 5 items (index 0-4)
        assertEquals(5, result.getPassedCount(), "Should have 5 passing scenarios matching v1 behavior");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineGeneratorWithSetupOnce() throws Exception {
        Path feature = tempDir.resolve("dynamic-generator-cached.feature");
        Files.writeString(feature, """
            Feature: Generator Function with SetupOnce

            @setup
            Scenario: Define generator
            * def generator = function(i){ if (i == 2) return null; return { seq: i } }

            Scenario Outline: First use of generator
            * match <seq> == __num

            Examples:
            | karate.setupOnce().generator |

            Scenario Outline: Second use (cached)
            * match <seq> == __num

            Examples:
            | karate.setupOnce().generator |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Generator evaluated once, cached result used twice: 2 + 2 = 4 scenarios
        assertEquals(4, result.getPassedCount(), "Should have 4 passing scenarios (2 from each outline using cached data)");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineGeneratorReturningEmptyImmediately() throws Exception {
        Path feature = tempDir.resolve("dynamic-generator-empty.feature");
        Files.writeString(feature, """
            Feature: Generator Returns Null Immediately

            @setup
            Scenario: Generator that returns null on first call
            * def generator = function(i){ return null }

            Scenario Outline: Should not run
            * def x = <val>

            Examples:
            | karate.setup().generator |

            Scenario: Regular scenario
            * def ran = true
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        // Generator returns null immediately, so no outline scenarios run
        assertEquals(1, result.getPassedCount(), "Only regular scenario should run");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithUnnamedScenario() throws Exception {
        // Tests that Scenario Outline without a name works correctly
        // Regression test for NPE in Scenario.replace() when name is null
        Path feature = tempDir.resolve("dynamic-unnamed.feature");
        Files.writeString(feature, """
            Feature:

            @setup
            Scenario:
            * def data = [{ name: 'one' }, { name: 'two' }]

            Scenario Outline:
            * match name == "#present"

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(2, result.getPassedCount(), "Should have 2 passing scenarios from dynamic data");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithTableKeyword() throws Exception {
        // Tests that table keyword in @setup scenario works with dynamic outline
        Path feature = tempDir.resolve("dynamic-table.feature");
        Files.writeString(feature, """
            Feature:

            @setup
            Scenario:
            * table data
                | name  | extra   |
                | 'one' |         |
                | 'two' | 'value' |

            Scenario Outline:
            * assert name == 'one' || name == 'two'

            Examples:
            | karate.setup().data |
            """);

        Feature f = Feature.read(Resource.from(feature, tempDir));
        FeatureRuntime fr = new FeatureRuntime(null, f);
        FeatureResult result = fr.call();

        assertEquals(2, result.getPassedCount(), "Should have 2 passing scenarios from table data");
        assertEquals(0, result.getFailedCount());
    }

    @Test
    void testDynamicOutlineWithCsvFile() throws Exception {
        // Test that read('file.csv') works in dynamic Examples
        Path csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
            name,value
            first,1
            second,2
            """);

        Path feature = tempDir.resolve("outline-csv.feature");
        Files.writeString(feature, """
            Feature:

            Scenario Outline: name is <name>
            * match __row == { name: '#string', value: '#string' }

            Examples:
            | read('data.csv') |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), "CSV-based outline should pass: " + getFailureMessage(result));
        assertEquals(2, result.getScenarioCount(), "Should have 2 scenarios from CSV");
    }

    // ========== Lifecycle Hook Tests ==========

    // Static counter for testing hooks (reset before each test)
    private static int afterScenarioOutlineCount = 0;

    @Test
    void testAfterScenarioOutlineHook() throws Exception {
        // Test that configure afterScenarioOutline is called once after all examples complete
        afterScenarioOutlineCount = 0;  // Reset counter

        Path feature = tempDir.resolve("after-outline-hook.feature");
        Files.writeString(feature, """
            Feature: afterScenarioOutline Hook

            Scenario Outline: Test <name>
            * configure afterScenarioOutline = function(){ Java.type('io.karatelabs.core.OutlineTest').incrementAfterOutlineCount() }
            * match '<name>' != ''

            Examples:
            | name  |
            | alice |
            | bob   |
            | carol |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
        // Verify hook was called exactly once (after all 3 examples completed)
        assertEquals(1, afterScenarioOutlineCount, "afterScenarioOutline should be called once after all examples complete");
    }

    @Test
    void testAfterScenarioOutlineHookMultipleOutlines() throws Exception {
        // Test that afterScenarioOutline is called once per outline
        afterScenarioOutlineCount = 0;  // Reset counter

        Path feature = tempDir.resolve("after-outline-multi.feature");
        Files.writeString(feature, """
            Feature: Multiple Outlines with Hook

            Scenario Outline: First outline <val>
            * configure afterScenarioOutline = function(){ Java.type('io.karatelabs.core.OutlineTest').incrementAfterOutlineCount() }
            * match <val> == <val>

            Examples:
            | val! |
            | 1    |
            | 2    |

            Scenario Outline: Second outline <val>
            * configure afterScenarioOutline = function(){ Java.type('io.karatelabs.core.OutlineTest').incrementAfterOutlineCount() }
            * match <val> == <val>

            Examples:
            | val! |
            | 10   |
            | 20   |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(4, result.getScenarioCount());
        // Verify hook was called twice (once per outline)
        assertEquals(2, afterScenarioOutlineCount, "afterScenarioOutline should be called once per outline");
    }

    @Test
    void testAfterScenarioOutlineHookNotCalledForRegularScenario() throws Exception {
        // Test that afterScenarioOutline is NOT called for regular scenarios
        afterScenarioOutlineCount = 0;  // Reset counter

        Path feature = tempDir.resolve("after-outline-regular.feature");
        Files.writeString(feature, """
            Feature: Regular Scenario Should Not Trigger Outline Hook

            Scenario: Regular scenario with outline hook configured
            * configure afterScenarioOutline = function(){ Java.type('io.karatelabs.core.OutlineTest').incrementAfterOutlineCount() }
            * def x = 1

            Scenario: Another regular scenario
            * def y = 2
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        // Verify hook was NOT called (since there are no outlines)
        assertEquals(0, afterScenarioOutlineCount, "afterScenarioOutline should not be called for regular scenarios");
    }

    // Static method for test hooks to call
    public static void incrementAfterOutlineCount() {
        afterScenarioOutlineCount++;
    }

    // ========== Empty Cell Placeholder Tests ==========

    @Test
    void testEmptyCellPlaceholderSubstitution() throws Exception {
        // Issue: When Examples table cell is empty, <placeholder> should become empty string
        // Bug: Currently leaves literal <placeholder> text instead of substituting
        Path feature = tempDir.resolve("empty-cell-placeholder.feature");
        Files.writeString(feature, """
            Feature: Empty Cell Placeholder Substitution

            Scenario Outline: Test with empty cells
            * def query = { name: '<name>', country: '<country>' }
            * print query
            * match query.name == '<name>'
            * match query.country == '<country>'

            Examples:
            | name | country |
            | foo  | US      |
            |      | JP      |
            | bar  |         |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(3, result.getScenarioCount());
    }

    @Test
    void testEmptyCellPlaceholderDoesNotContainLiteralPlaceholder() throws Exception {
        // Issue: When Examples table cell is empty, the placeholder text should NOT appear literally
        // Verify that <name> doesn't show up as the string "<name>" when cell is empty
        Path feature = tempDir.resolve("empty-cell-no-literal.feature");
        Files.writeString(feature, """
            Feature: Empty Cell Should Not Leave Literal Placeholder

            Scenario Outline: Test row <__num>
            * def nameVal = '<name>'
            * def countryVal = '<country>'
            * print 'nameVal=' + nameVal + ', countryVal=' + countryVal
            # When cell is empty, the placeholder should be replaced with empty string, not literal '<name>'
            * match nameVal != '<' + 'name>'
            * match countryVal != '<' + 'country>'

            Examples:
            | name | country |
            | foo  | US      |
            |      | JP      |
            | bar  |         |
            |      |         |
            """);

        SuiteResult result = runTestSuite(tempDir, feature.toString());

        assertTrue(result.isPassed(), getFailureMessage(result));
        assertEquals(4, result.getScenarioCount());
    }

    // ========== Helper Methods ==========

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
