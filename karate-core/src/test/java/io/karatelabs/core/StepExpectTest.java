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
 * Tests for karate.expect() chai-style BDD assertion API.
 */
class StepExpectTest {

    // ========== Equality Assertions ==========

    @Test
    void testExpectEqual() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.equal(1)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectEqualFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.equal(2)
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectEql() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test' }
            * karate.expect(obj).to.eql({ name: 'test' })
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectEqlFailure() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test' }
            * karate.expect(obj).to.eql({ name: 'other' })
            """);
        assertFailed(sr);
    }

    // ========== Type Assertions ==========

    @Test
    void testExpectTypeString() {
        ScenarioRuntime sr = run("""
            * karate.expect('hello').to.be.a('string')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectTypeNumber() {
        ScenarioRuntime sr = run("""
            * karate.expect(42).to.be.a('number')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectTypeArray() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2, 3]).to.be.an('array')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectTypeObject() {
        ScenarioRuntime sr = run("""
            * karate.expect({ foo: 'bar' }).to.be.an('object')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectTypeBoolean() {
        ScenarioRuntime sr = run("""
            * karate.expect(true).to.be.a('boolean')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectTypeFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect(42).to.be.a('string')
            """);
        assertFailed(sr);
    }

    // ========== Boolean/Null Assertions ==========

    @Test
    void testExpectTrue() {
        ScenarioRuntime sr = run("""
            * karate.expect(true).to.be.true
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectFalse() {
        ScenarioRuntime sr = run("""
            * karate.expect(false).to.be.false
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNull() {
        ScenarioRuntime sr = run("""
            * def x = null
            * karate.expect(x).to.be.null
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectOk() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.be.ok
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNotOk() {
        ScenarioRuntime sr = run("""
            * karate.expect(0).to.be.ok
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectExist() {
        ScenarioRuntime sr = run("""
            * def x = 'value'
            * karate.expect(x).to.exist
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectExistFailure() {
        ScenarioRuntime sr = run("""
            * def x = null
            * karate.expect(x).to.exist
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectEmpty() {
        ScenarioRuntime sr = run("""
            * karate.expect('').to.be.empty
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectEmptyArray() {
        ScenarioRuntime sr = run("""
            * karate.expect([]).to.be.empty
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectEmptyObject() {
        ScenarioRuntime sr = run("""
            * karate.expect({}).to.be.empty
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNotEmpty() {
        ScenarioRuntime sr = run("""
            * karate.expect('foo').to.be.empty
            """);
        assertFailed(sr);
    }

    // ========== Negation ==========

    @Test
    void testExpectNotEqual() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.not.equal(2)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNotEqualFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.not.equal(1)
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectNotNull() {
        ScenarioRuntime sr = run("""
            * karate.expect(1).to.not.be.null
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNotExist() {
        ScenarioRuntime sr = run("""
            * def x = null
            * karate.expect(x).to.not.exist
            """);
        assertPassed(sr);
    }

    // ========== Containment ==========

    @Test
    void testExpectIncludeString() {
        ScenarioRuntime sr = run("""
            * karate.expect('hello world').to.include('world')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectIncludeArray() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2, 3]).to.include(2)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectIncludeObject() {
        ScenarioRuntime sr = run("""
            * def obj = { a: 1, b: 2, c: 3 }
            * karate.expect(obj).to.include({ b: 2 })
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectContain() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2, 3]).to.contain(2)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectDeepInclude() {
        ScenarioRuntime sr = run("""
            * def obj = { a: { b: { c: 1 } } }
            * karate.expect(obj).to.deep.include({ a: { b: { c: 1 } } })
            """);
        assertPassed(sr);
    }

    // ========== Property Assertions ==========

    @Test
    void testExpectHaveProperty() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test', value: 42 }
            * karate.expect(obj).to.have.property('name')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHavePropertyValue() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test', value: 42 }
            * karate.expect(obj).to.have.property('value', 42)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHavePropertyFailure() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test' }
            * karate.expect(obj).to.have.property('missing')
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectHaveNestedProperty() {
        ScenarioRuntime sr = run("""
            * def obj = { a: { b: { c: 3 } } }
            * karate.expect(obj).to.have.nested.property('a.b.c')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveNestedPropertyWithValue() {
        ScenarioRuntime sr = run("""
            * def obj = { a: { b: { c: 3 } } }
            * karate.expect(obj).to.have.nested.property('a.b.c', 3)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveKeys() {
        ScenarioRuntime sr = run("""
            * def obj = { a: 1, b: 2 }
            * karate.expect(obj).to.have.keys(['a', 'b'])
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveAllKeys() {
        ScenarioRuntime sr = run("""
            * def obj = { a: 1, b: 2 }
            * karate.expect(obj).to.have.all.keys(['a'])
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveAnyKeys() {
        ScenarioRuntime sr = run("""
            * def obj = { a: 1, b: 2 }
            * karate.expect(obj).to.have.any.keys(['a', 'c'])
            """);
        assertPassed(sr);
    }

    // ========== Length Assertions ==========

    @Test
    void testExpectHaveLength() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2, 3]).to.have.length(3)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveLengthString() {
        ScenarioRuntime sr = run("""
            * karate.expect('hello').to.have.length(5)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectHaveLengthFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2]).to.have.length(3)
            """);
        assertFailed(sr);
    }

    // ========== Numeric Assertions ==========

    @Test
    void testExpectAbove() {
        ScenarioRuntime sr = run("""
            * karate.expect(10).to.be.above(5)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectBelow() {
        ScenarioRuntime sr = run("""
            * karate.expect(5).to.be.below(10)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectAtLeast() {
        ScenarioRuntime sr = run("""
            * karate.expect(10).to.be.at.least(10)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectAtMost() {
        ScenarioRuntime sr = run("""
            * karate.expect(10).to.be.at.most(10)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectWithin() {
        ScenarioRuntime sr = run("""
            * karate.expect(10).to.be.within(5, 15)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectWithinFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect(20).to.be.within(5, 15)
            """);
        assertFailed(sr);
    }

    @Test
    void testExpectCloseTo() {
        ScenarioRuntime sr = run("""
            * karate.expect(3.14).to.be.closeTo(3.1, 0.05)
            """);
        assertPassed(sr);
    }

    // ========== Regex Assertions ==========

    @Test
    void testExpectMatch() {
        ScenarioRuntime sr = run("""
            * karate.expect('hello').to.match(/^hel/)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectMatchFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect('hello').to.match(/^abc/)
            """);
        assertFailed(sr);
    }

    // ========== OneOf Assertions ==========

    @Test
    void testExpectOneOf() {
        ScenarioRuntime sr = run("""
            * karate.expect(2).to.be.oneOf([1, 2, 3])
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectOneOfFailure() {
        ScenarioRuntime sr = run("""
            * karate.expect(5).to.be.oneOf([1, 2, 3])
            """);
        assertFailed(sr);
    }

    // ========== Chaining ==========

    @Test
    void testExpectChainTypeAndProperty() {
        ScenarioRuntime sr = run("""
            * def obj = { name: 'test' }
            * karate.expect(obj).to.be.an('object').and.have.property('name')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectChainTypeAndLength() {
        ScenarioRuntime sr = run("""
            * def arr = [1, 2, 3]
            * karate.expect(arr).to.be.an('array').and.have.length(3)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectPropertyChainWithEqual() {
        ScenarioRuntime sr = run("""
            * def obj = { source: 'test-value' }
            * karate.expect(obj).to.have.property('source').and.equal('test-value')
            """);
        assertPassed(sr);
    }

    // ========== Shorthand Syntax ==========

    @Test
    void testExpectShorthandIs() {
        ScenarioRuntime sr = run("""
            * karate.expect([]).is.empty
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectShorthandHas() {
        ScenarioRuntime sr = run("""
            * karate.expect({ a: 1 }).has.property('a')
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectShorthandInclude() {
        ScenarioRuntime sr = run("""
            * karate.expect([1, 2]).include(1)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectShorthandEqual() {
        ScenarioRuntime sr = run("""
            * karate.expect(5).equal(5)
            """);
        assertPassed(sr);
    }

    // ========== Decimal Number Tests ==========

    @Test
    void testExpectDecimalEqual() {
        ScenarioRuntime sr = run("""
            * karate.expect(25.9286).to.eql(25.9286)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectDecimalWithin() {
        ScenarioRuntime sr = run("""
            * karate.expect(25.9286).to.be.within(25.5, 26.5)
            """);
        assertPassed(sr);
    }

    @Test
    void testExpectNegativeDecimalWithin() {
        ScenarioRuntime sr = run("""
            * karate.expect(-80.183).to.be.within(-81, -80)
            """);
        assertPassed(sr);
    }

}
