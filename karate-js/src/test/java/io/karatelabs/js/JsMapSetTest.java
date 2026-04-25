package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsMapSetTest extends EvalBase {

    @Test
    void mapBasics() {
        assertEquals(0, eval("new Map().size"));
        assertEquals(2, eval("var m = new Map(); m.set('a', 1); m.set('b', 2); m.size"));
        assertEquals(1, eval("var m = new Map(); m.set('a', 1); m.get('a')"));
        assertEquals(true, eval("var m = new Map(); m.set('a', 1); m.has('a')"));
        assertEquals(false, eval("var m = new Map(); m.has('a')"));
        assertEquals(true, eval("var m = new Map(); m.set('a', 1); m.delete('a')"));
        assertEquals(false, eval("var m = new Map(); m.delete('missing')"));
    }

    @Test
    void mapFromIterable() {
        assertEquals(2, eval("new Map([['a',1],['b',2]]).size"));
        assertEquals(1, eval("new Map([['a',1],['b',2]]).get('a')"));
    }

    @Test
    void mapNormalizesNegativeZero() {
        // Spec SameValueZero: -0 and +0 collide in Map keys.
        assertEquals(42, eval("var m = new Map(); m.set(-0, 42); m.get(0)"));
        assertEquals(1, eval("var m = new Map(); m.set(-0, 42); m.set(0, 1); m.size"));
    }

    @Test
    void mapNaNKey() {
        // Spec SameValueZero: NaN === NaN as a Map key.
        assertEquals(7, eval("var m = new Map(); m.set(NaN, 7); m.get(NaN)"));
    }

    @Test
    void mapForEachAndIteration() {
        assertEquals(3, eval("var m = new Map([['a',1],['b',2]]); var t = 0; m.forEach(function(v){ t += v; }); t"));
        Object keys = eval("var m = new Map([['a',1],['b',2]]); var out = []; for (var k of m.keys()) out.push(k); out");
        assertEquals(List.of("a", "b"), keys);
    }

    @Test
    void mapEntriesIteration() {
        // Default iteration is entries().
        Object pairs = eval("var m = new Map([['a',1],['b',2]]); var out = []; for (var e of m) out.push(e[0] + ':' + e[1]); out");
        assertEquals(List.of("a:1", "b:2"), pairs);
    }

    @Test
    void mapClear() {
        assertEquals(0, eval("var m = new Map([['a',1]]); m.clear(); m.size"));
    }

    @Test
    void mapWithoutNewThrows() {
        assertThrows(Exception.class, () -> eval("Map()"));
    }

    @Test
    void setBasics() {
        assertEquals(0, eval("new Set().size"));
        assertEquals(2, eval("var s = new Set(); s.add(1); s.add(2); s.size"));
        assertEquals(1, eval("var s = new Set(); s.add(1); s.add(1); s.size"));
        assertEquals(true, eval("var s = new Set(); s.add('x'); s.has('x')"));
        assertEquals(false, eval("new Set().has('x')"));
        assertEquals(true, eval("var s = new Set(); s.add('x'); s.delete('x')"));
    }

    @Test
    void setFromIterable() {
        assertEquals(3, eval("new Set([1,2,3,2,1]).size"));
    }

    @Test
    void setNormalizesNegativeZero() {
        assertEquals(1, eval("var s = new Set(); s.add(-0); s.add(0); s.size"));
        assertEquals(true, eval("var s = new Set(); s.add(-0); s.has(0)"));
    }

    @Test
    void setIteration() {
        Object values = eval("var s = new Set([1,2,3]); var out = []; for (var v of s) out.push(v); out");
        assertEquals(List.of(1, 2, 3), values);
    }

    @Test
    void setForEachReceivesValueTwice() {
        // Spec: Set.prototype.forEach passes (value, value, set).
        assertEquals(true, eval("var s = new Set([1]); var same = false; s.forEach(function(v,k){ same = (v === k); }); same"));
    }

    @Test
    void setWithoutNewThrows() {
        assertThrows(Exception.class, () -> eval("Set()"));
    }

    @Test
    void mapAndSetTypeof() {
        assertEquals("function", eval("typeof Map"));
        assertEquals("function", eval("typeof Set"));
        assertEquals("object", eval("typeof new Map()"));
        assertEquals("object", eval("typeof new Set()"));
    }

    @Test
    void stringMatchAll() {
        // Each match is a JsArray with `index` and `input` properties.
        Object first = eval("var it = 'hello world'.matchAll(/o/g); var r = it.next(); r.value[0]");
        assertEquals("o", first);
        assertEquals(2, eval("var c = 0; for (var m of 'hello world'.matchAll(/o/g)) c++; c"));
        // 'hello' → l at indexes 2 and 3
        assertEquals(3, eval("var arr = []; for (var m of 'hello'.matchAll(/l/g)) arr.push(m.index); arr[1]"));
        // Capture groups exposed as elements 1..n.
        assertEquals("a", eval("var m = 'aXbY'.matchAll(/(.)X/g).next().value; m[1]"));
    }

    @Test
    void stringMatchAllNonGlobalThrows() {
        assertThrows(Exception.class, () -> eval("'abc'.matchAll(/a/)"));
    }

}
