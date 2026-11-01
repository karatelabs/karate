package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsTypedArrayTest extends EvalBase {

    @Test
    void testArrayBuffer() {
        assertEquals(8, eval("new ArrayBuffer(8).byteLength"));
        assertEquals(4, eval("new ArrayBuffer(10).slice(2, -4).byteLength"));
        assertEquals("[object ArrayBuffer]", eval("Object.prototype.toString.call(new ArrayBuffer(1))"));
        assertEquals(true, eval("new ArrayBuffer(1) instanceof ArrayBuffer"));
        assertEquals("true,true,false", eval("[ArrayBuffer.isView(new Uint8Array(1)), ArrayBuffer.isView(new DataView(new ArrayBuffer(1))),"
                + " ArrayBuffer.isView(new ArrayBuffer(1))].join()"));
        assertEquals("TypeError", eval("try { ArrayBuffer(1) } catch (e) { e.name }"));
        assertEquals("RangeError", eval("try { new ArrayBuffer(-1) } catch (e) { e.name }"));
    }

    @Test
    void testViewsShareTheBuffer() {
        assertEquals("1,true,513", eval("var b = new ArrayBuffer(4); var u = new Uint8Array(b); u[0] = 1; u[1] = 2;"
                + " var i = new Int16Array(b); [u[0], u.buffer === b, i[0]].join()"));
        assertEquals("1,9,3,4|1,2", eval("var x = new Uint8Array([1, 2, 3, 4]); var y = x.subarray(1, 3); y[0] = 9;"
                + " x.join() + '|' + y.byteOffset + ',' + y.length"));
        assertEquals("2,3", eval("new Uint8Array(new ArrayBuffer(4), 1, 2).map((v, k) => k + 2).join()"));
        assertEquals("RangeError", eval("try { new Int16Array(new ArrayBuffer(3)) } catch (e) { e.name }"));
        assertEquals("RangeError", eval("try { new Int16Array(new ArrayBuffer(4), 1) } catch (e) { e.name }"));
        assertEquals("RangeError", eval("try { new Uint8Array(new ArrayBuffer(4), 2, 3) } catch (e) { e.name }"));
    }

    @Test
    void testElementConversion() {
        assertEquals("-1,-128", eval("new Int8Array([255, 128]).join()"));
        assertEquals("255,0,2,2", eval("new Uint8ClampedArray([300, -5, 1.5, 2.5]).join()"));
        assertEquals("4464,65535", eval("[new Int16Array([70000])[0], new Uint16Array([-1])[0]].join()"));
        assertEquals("3,-3", eval("new Int32Array([3.9, -3.9]).join()"));
        assertEquals(4294967295L, eval("new Uint32Array([-1])[0]"));
        assertEquals(0.10000000149011612, eval("new Float32Array([0.1])[0]"));
        assertEquals(7, eval("var u = new Uint8Array(1); u[0] = '7'; u[0]"));
    }

    @Test
    void testFixedLength() {
        assertEquals(2, eval("var u = new Uint8Array(2); u[5] = 1; u.length"));
        assertEquals(1, eval("var w = new Uint32Array(1); w[3] = 1; w.length"));
        assertEquals(2, eval("var z = new Uint8Array(2); z.length = 5; z.length"));
        assertEquals("undefined", eval("typeof new Uint8Array(1).push"));
        assertEquals(true, eval("new Uint8Array(1)[1] === undefined"));
    }

    @Test
    void testConstructorsAndPrototypes() {
        assertEquals("function", eval("typeof Float64Array"));
        assertEquals("2,2,3", eval("[Uint16Array.BYTES_PER_ELEMENT, new Uint16Array(1).BYTES_PER_ELEMENT, Int8Array.length].join()"));
        assertEquals("true,false,false", eval("var u = new Uint8Array(1); [u instanceof Uint8Array, u instanceof Int8Array, u instanceof Array].join()"));
        assertEquals(false, eval("Array.isArray(new Float32Array(1))"));
        assertEquals("[object Uint8Array][object Int16Array]", eval("Object.prototype.toString.call(new Uint8Array(1))"
                + " + Object.prototype.toString.call(new Int16Array(1))"));
        assertEquals(true, eval("Object.getPrototypeOf(Int8Array.prototype) === Object.getPrototypeOf(Uint8Array.prototype)"));
        assertEquals(true, eval("Uint8Array.prototype.constructor === Uint8Array"));
        assertEquals("TypeError", eval("try { Uint8Array(2) } catch (e) { e.name }"));
        assertEquals("Invalid typed array length: -1", eval("try { new Uint8Array(-1) } catch (e) { e.message }"));
        assertEquals("1,2", eval("Uint8Array.from([1, 2]).join()"));
        assertEquals("97,98", eval("Uint8Array.from('ab', c => c.charCodeAt(0)).join()"));
        assertEquals("1,2", eval("Int8Array.of(1, 2).join()"));
        assertEquals("1,2", eval("new Uint8Array({length: 2, 0: 1, 1: 2}).join()"));
    }

    @Test
    void testMethods() {
        assertEquals("200,144", eval("new Uint8Array([1, 2]).map(x => x * 200).join()"));
        assertEquals(true, eval("new Int16Array([1, 2, 3]).filter(x => x > 1) instanceof Int16Array"));
        assertEquals("-2,1,3", eval("new Int8Array([1, -2, 3]).sort().join()"));
        assertEquals("3,2,1", eval("new Uint16Array([3, 1, 2]).sort((a, b) => b - a).join()"));
        assertEquals("9,9,9", eval("new Uint8Array(3).fill(9).join()"));
        assertEquals("2,3", eval("new Uint8Array([1, 2, 3]).slice(1).join()"));
        assertEquals("0,0,1,2", eval("var s = new Uint8Array(4); s.set([1, 2], 2); s.join()"));
        assertEquals("1,2", eval("[...new Int16Array([1, 2])].join()"));
        assertEquals("1,2", eval("new Uint8Array([1, 2]).toString()"));
        assertEquals(1, eval("new Float64Array([5, 6]).indexOf(6)"));
        assertEquals("TypeError", eval("try { new Uint8Array(1).foo() } catch (e) { e.name }"));
    }

    @Test
    void testBinaryStringRoundTrip() {
        assertEquals("Hi", eval("String.fromCharCode.apply(null, new Uint8Array([72, 105]))"));
        assertEquals(true, eval("var bin = '\\u00ff\\u0001A'; var u = new Uint8Array(bin.length);"
                + " for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); String.fromCharCode.apply(null, u) === bin"));
        assertEquals("hi", eval("new TextDecoder().decode(new Uint8Array([104, 105]).buffer)"));
        assertEquals("ij", eval("new TextDecoder().decode(new Uint8Array([104, 105, 106]).subarray(1))"));
    }

    @Test
    void testUint8ArrayCrossesToJavaAsBytes() {
        assertArrayEquals(new byte[]{1, 2}, (byte[]) eval("new Uint8Array([1, 2])"));
        assertArrayEquals(new byte[]{2, 3}, (byte[]) eval("new Uint8Array([1, 2, 3, 4]).subarray(1, 3)"));
    }

    @Test
    void testDataView() {
        assertEquals("-2,254,3735928559,4022250974", eval("var d = new DataView(new ArrayBuffer(8)); d.setInt16(0, -2);"
                + " d.setUint32(4, 0xdeadbeef, true); [d.getInt16(0), d.getUint8(1), d.getUint32(4, true), d.getUint32(4)].join()"));
        assertEquals(1.5, eval("var d = new DataView(new ArrayBuffer(4)); d.setFloat32(0, 1.5); d.getFloat32(0)"));
        assertEquals("-1,18446744073709551615", eval("var d = new DataView(new ArrayBuffer(8)); d.setBigUint64(0, 2n ** 64n - 1n);"
                + " d.getBigInt64(0) + ',' + d.getBigUint64(0)"));
        assertEquals(3, eval("new DataView(new ArrayBuffer(4), 1).byteLength"));
        assertEquals("RangeError", eval("try { new DataView(new ArrayBuffer(2)).getInt32(0) } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new DataView({}) } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new DataView(new ArrayBuffer(8)).setBigInt64(0, 1) } catch (e) { e.name }"));
    }

    @Test
    void testCryptoFillsAnyIntegerView() {
        assertEquals(2, eval("crypto.getRandomValues(new Uint32Array(2)).length"));
        assertEquals("TypeError", eval("try { crypto.getRandomValues(new Float32Array(1)) } catch (e) { e.name }"));
    }

}
