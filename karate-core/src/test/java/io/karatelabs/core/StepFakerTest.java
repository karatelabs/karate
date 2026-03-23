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
 * Tests for karate.faker.* API.
 */
class StepFakerTest {

    // ========== Timestamps ==========

    @Test
    void testFakerTimestamp() {
        ScenarioRuntime sr = run("""
            * def ts = karate.faker.timestamp()
            * match ts == '#number'
            """);
        assertPassed(sr);
    }

    @Test
    void testFakerIsoTimestamp() {
        ScenarioRuntime sr = run("""
            * def iso = karate.faker.isoTimestamp()
            * match iso == '#regex \\\\d{4}-\\\\d{2}-\\\\d{2}T.*Z'
            """);
        assertPassed(sr);
    }

    // ========== Names ==========

    @Test
    void testFakerFirstName() {
        ScenarioRuntime sr = run("""
            * def name = karate.faker.firstName()
            * match name == '#string'
            """);
        assertPassed(sr);
    }

    @Test
    void testFakerFullName() {
        ScenarioRuntime sr = run("""
            * def name = karate.faker.fullName()
            * match name == '#string'
            * match name contains ' '
            """);
        assertPassed(sr);
    }

    // ========== Contact ==========

    @Test
    void testFakerEmail() {
        ScenarioRuntime sr = run("""
            * def email = karate.faker.email()
            * match email == '#regex .+@.+\\\\..+'
            """);
        assertPassed(sr);
    }

    // ========== Numbers ==========

    @Test
    void testFakerRandomInt() {
        ScenarioRuntime sr = run("""
            * def n = karate.faker.randomInt()
            * match n == '#number'
            * assert n >= 0
            * assert n <= 1000
            """);
        assertPassed(sr);
    }

    @Test
    void testFakerRandomIntWithRange() {
        ScenarioRuntime sr = run("""
            * def n = karate.faker.randomInt(10, 20)
            * match n == '#number'
            * assert n >= 10
            * assert n <= 20
            """);
        assertPassed(sr);
    }

    @Test
    void testFakerRandomBoolean() {
        ScenarioRuntime sr = run("""
            * def b = karate.faker.randomBoolean()
            * match b == '#boolean'
            """);
        assertPassed(sr);
    }

    // ========== Text ==========

    @Test
    void testFakerAlphanumeric() {
        ScenarioRuntime sr = run("""
            * def s = karate.faker.alphanumeric(8)
            * match s == '#regex [A-Za-z0-9]{8}'
            """);
        assertPassed(sr);
    }

    // ========== In Request Body ==========

    @Test
    void testFakerInRequestBody() {
        ScenarioRuntime sr = run("""
            * def body = { id: '#(karate.uuid())', name: '#(karate.faker.fullName())', email: '#(karate.faker.email())' }
            * match body.id == '#uuid'
            * match body.name == '#string'
            * match body.email == '#regex .+@.+\\\\..+'
            """);
        assertPassed(sr);
    }

}
