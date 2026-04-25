package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JsBigIntTest extends EvalBase {

    @Test
    void testLiteralAndTypeof() {
        assertEquals(BigInteger.valueOf(123), eval("123n"));
        assertEquals("bigint", eval("typeof 123n"));
        assertEquals(BigInteger.ZERO, eval("0n"));
    }

    @Test
    void testNumericSeparators() {
        assertEquals(1_000_000, eval("1_000_000"));
        assertEquals(0xff_ff, eval("0xff_ff"));
        assertEquals(BigInteger.valueOf(1_000_000), eval("1_000_000n"));
        assertEquals(new BigInteger("ffff", 16), eval("0xff_ffn"));
    }

    @Test
    void testNumericSeparatorErrors() {
        assertThrows(Exception.class, () -> eval("1__0"));    // doubled separator
        assertThrows(Exception.class, () -> eval("0x_ff"));   // leading separator after prefix is also invalid
    }

    @Test
    void testArithmetic() {
        assertEquals(BigInteger.valueOf(3), eval("1n + 2n"));
        assertEquals(BigInteger.valueOf(-1), eval("1n - 2n"));
        assertEquals(BigInteger.valueOf(6), eval("2n * 3n"));
        assertEquals(BigInteger.valueOf(3), eval("10n / 3n"));
        assertEquals(BigInteger.valueOf(1), eval("10n % 3n"));
        assertEquals(BigInteger.valueOf(8), eval("2n ** 3n"));
        assertEquals(new BigInteger("12345678901234567890"), eval("12345678901234567890n"));
    }

    @Test
    void testUnary() {
        assertEquals(BigInteger.valueOf(-1), eval("-1n"));
        assertEquals(BigInteger.valueOf(-5), eval("-(2n + 3n)"));
        assertInstanceOf(EngineException.class, assertThrows(Exception.class, () -> eval("+1n")));
    }

    @Test
    void testBitwise() {
        // 0xc=1100, 0xa=1010 — using hex since binary literals (0b) are out of scope here
        assertEquals(BigInteger.valueOf(0x8), eval("0xcn & 0xan"));
        assertEquals(BigInteger.valueOf(0xe), eval("0xcn | 0xan"));
        assertEquals(BigInteger.valueOf(0x6), eval("0xcn ^ 0xan"));
        assertEquals(BigInteger.valueOf(8), eval("2n << 2n"));
        assertEquals(BigInteger.valueOf(2), eval("8n >> 2n"));
        assertEquals(BigInteger.valueOf(-1), eval("~0n"));
    }

    @Test
    void testMixingThrowsTypeError() {
        // Mixing BigInt with Number must throw TypeError per spec
        assertThrows(Exception.class, () -> eval("1n + 1"));
        assertThrows(Exception.class, () -> eval("1n * 2"));
        assertThrows(Exception.class, () -> eval("1n - 1"));
        // unsigned right shift always throws on BigInt
        assertThrows(Exception.class, () -> eval("1n >>> 1n"));
    }

    @Test
    void testEquality() {
        assertEquals(true, eval("1n === 1n"));
        // Strict eq: BigInt vs Number is always false (different types)
        assertEquals(false, eval("1n === 1"));
        // Loose eq: numerically equal
        assertEquals(true, eval("1n == 1"));
        assertEquals(true, eval("1n == '1'"));
        assertEquals(false, eval("1n == 1.5"));
        assertEquals(false, eval("1n == 'abc'"));
    }

    @Test
    void testOrdering() {
        assertEquals(true, eval("1n < 2n"));
        assertEquals(true, eval("2n > 1n"));
        assertEquals(true, eval("1n <= 1n"));
        // mixed BigInt vs Number ordering uses mathematical comparison
        assertEquals(true, eval("1n < 2"));
        assertEquals(true, eval("2 > 1n"));
        assertEquals(true, eval("1n <= 1.0"));
    }

    @Test
    void testConstructor() {
        assertEquals(BigInteger.valueOf(42), eval("BigInt(42)"));
        assertEquals(BigInteger.valueOf(0), eval("BigInt(false)"));
        assertEquals(BigInteger.valueOf(1), eval("BigInt(true)"));
        assertEquals(new BigInteger("1000000000000000000000"), eval("BigInt('1000000000000000000000')"));
        assertEquals(new BigInteger("ff", 16), eval("BigInt('0xff')"));
        assertThrows(Exception.class, () -> eval("BigInt(1.5)"));
        assertThrows(Exception.class, () -> eval("BigInt('not a number')"));
        assertThrows(Exception.class, () -> eval("BigInt()"));
    }

    @Test
    void testNumberToBigInt() {
        // Number(bigint) collapses precision but is allowed
        assertEquals(1, eval("Number(1n)"));
        assertEquals("number", eval("typeof Number(1n)"));
    }

    @Test
    void testToString() {
        assertEquals("123", eval("(123n).toString()"));
        assertEquals("ff", eval("(255n).toString(16)"));
        assertEquals("1100", eval("(12n).toString(2)"));
        assertEquals("-7b", eval("(-123n).toString(16)"));
    }

    @Test
    void testPrototypeMethodViaCall() {
        // BigInt.prototype.valueOf.call(0n) — `this` is BigInteger.ZERO directly
        assertEquals(BigInteger.ZERO, eval("BigInt.prototype.valueOf.call(0n)"));
        assertEquals(BigInteger.valueOf(123), eval("BigInt.prototype.valueOf.call(123n)"));
        assertEquals("ff", eval("BigInt.prototype.toString.call(255n, 16)"));
    }

    @Test
    void testIncrementDecrement() {
        // ++ / -- on BigInt must increment/decrement by 1n, not Number 1
        assertEquals(BigInteger.valueOf(2), eval("var i = 1n; i++; i"));
        assertEquals(BigInteger.valueOf(0), eval("var i = 1n; i--; i"));
        assertEquals(BigInteger.valueOf(2), eval("var i = 1n; ++i"));
        assertEquals(BigInteger.valueOf(0), eval("var i = 1n; --i"));
        // Also verify in a loop
        assertEquals(BigInteger.valueOf(15), eval("var s = 0n; for (var i = 0n; i < 5n; i++) { s += i + 1n; } s"));
    }

    @Test
    void testJsonStringifyThrows() {
        assertThrows(Exception.class, () -> eval("JSON.stringify(1n)"));
        assertThrows(Exception.class, () -> eval("JSON.stringify({a: 1n})"));
        assertThrows(Exception.class, () -> eval("JSON.stringify([1, 2n, 3])"));
    }

    @Test
    void testToPrimitive() {
        // BigInt() should call valueOf() on object inputs (ToPrimitive hint "number")
        assertEquals(BigInteger.valueOf(42), eval("BigInt({valueOf: function() { return 42n; }})"));
        assertEquals(BigInteger.valueOf(7), eval("BigInt({valueOf: function() { return '7'; }})"));
        // valueOf returning a non-integer Number is a RangeError per ToBigInt
        assertThrows(Exception.class, () -> eval("BigInt({valueOf: function() { return 1.5; }})"));
        // valueOf returning an object falls through to toString
        assertEquals(BigInteger.valueOf(99), eval(
            "BigInt({valueOf: function() { return {}; }, toString: function() { return '99'; }})"));
    }

    @Test
    void testNumberToPrimitive() {
        // Number() should also call valueOf() on object inputs
        assertEquals(42, eval("Number({valueOf: function() { return 42; }})"));
        assertEquals(0, eval("Number({valueOf: function() { return false; }})"));
    }

    @Test
    void testAsIntNAsUintN() {
        // Truncation to N bits
        assertEquals(BigInteger.valueOf(-1), eval("BigInt.asIntN(8, 255n)"));
        assertEquals(BigInteger.valueOf(255), eval("BigInt.asUintN(8, 255n)"));
        assertEquals(BigInteger.valueOf(0), eval("BigInt.asIntN(8, 256n)"));
    }

}
