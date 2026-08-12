package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsWeakMapSetTest extends EvalBase {

    @Test
    void weakMapBasics() {
        assertEquals(1, eval("var k = {}; var m = new WeakMap(); m.set(k, 1); m.get(k)"));
        assertEquals(true, eval("var k = {}; var m = new WeakMap(); m.set(k, 1); m.has(k)"));
        assertEquals(false, eval("var k = {}; new WeakMap().has(k)"));
        assertEquals(true, eval("var k = {}; var m = new WeakMap(); m.set(k, 1); m.delete(k)"));
        assertEquals(false, eval("var k = {}; var m = new WeakMap(); m.set(k, 1); m.delete(k); m.has(k)"));
        assertEquals(false, eval("var k = {}; new WeakMap().delete(k)"));
        assertEquals("undefined", eval("var k = {}; typeof new WeakMap().get(k)"));
        // last write wins for the same key
        assertEquals(2, eval("var k = {}; var m = new WeakMap(); m.set(k, 1); m.set(k, 2); m.get(k)"));
    }

    @Test
    void weakMapKeysAreIdentityBased() {
        // structurally-equal but distinct objects are distinct keys
        assertEquals(false, eval("var m = new WeakMap(); m.set({a: 1}, 'x'); m.has({a: 1})"));
        assertEquals(false, eval("var m = new WeakMap(); m.set([1], 'x'); m.has([1])"));
        assertEquals("x", eval("var k = [1]; var m = new WeakMap(); m.set(k, 'x'); m.get(k)"));
        // functions are valid keys
        assertEquals(9, eval("var f = function(){}; var m = new WeakMap(); m.set(f, 9); m.get(f)"));
    }

    @Test
    void weakMapSetIsChainable() {
        assertEquals(true, eval("var k = {}; var m = new WeakMap(); m.set(k, 1) === m"));
        assertEquals(2, eval("var a = {}, b = {}; var m = new WeakMap(); m.set(a, 1).set(b, 2); m.get(b)"));
    }

    @Test
    void weakMapFromIterable() {
        assertEquals(1, eval("var a = {}, b = {}; new WeakMap([[a, 1], [b, 2]]).get(a)"));
        assertEquals(2, eval("var a = {}, b = {}; new WeakMap([[a, 1], [b, 2]]).get(b)"));
        assertEquals(true, eval("var a = {}; new WeakMap(new Set([[a, 1]])).has(a)"));
    }

    @Test
    void weakMapPrimitiveKeyThrows() {
        assertEquals("TypeError: Invalid value used as weak map key",
                eval("try { new WeakMap().set('a', 1); 'no-throw' } catch (e) { e.name + ': ' + e.message }"));
        assertEquals("TypeError", eval("try { new WeakMap().set(1, 1); 'no-throw' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new WeakMap().set(null, 1); 'no-throw' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new WeakMap().set(undefined, 1); 'no-throw' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new WeakMap([['a', 1]]); 'no-throw' } catch (e) { e.name }"));
    }

    @Test
    void weakMapPrimitiveLookupsDoNotThrow() {
        // spec: only `set` validates the key — get/has/delete just miss
        assertEquals("undefined", eval("typeof new WeakMap().get('a')"));
        assertEquals(false, eval("new WeakMap().has('a')"));
        assertEquals(false, eval("new WeakMap().delete('a')"));
        assertEquals(false, eval("new WeakMap().has(null)"));
        assertEquals(false, eval("new WeakMap().delete(undefined)"));
    }

    @Test
    void weakMapHasNoSizeOrIteration() {
        assertEquals("undefined", eval("typeof new WeakMap().size"));
        assertEquals("undefined", eval("typeof new WeakMap().forEach"));
        assertEquals("undefined", eval("typeof new WeakMap().clear"));
        assertEquals("undefined", eval("typeof new WeakMap().keys"));
        assertEquals("undefined", eval("typeof new WeakMap().values"));
        assertEquals("undefined", eval("typeof new WeakMap().entries"));
        assertThrows(Exception.class, () -> eval("for (var e of new WeakMap()) {}"));
    }

    @Test
    void weakMapShape() {
        assertEquals("function", eval("typeof WeakMap"));
        assertEquals("object", eval("typeof new WeakMap()"));
        assertEquals(true, eval("new WeakMap() instanceof WeakMap"));
        assertEquals("[object WeakMap]", eval("Object.prototype.toString.call(new WeakMap())"));
    }

    @Test
    void weakMapWithoutNewThrows() {
        assertThrows(Exception.class, () -> eval("WeakMap()"));
    }

    @Test
    void weakSetBasics() {
        assertEquals(true, eval("var v = {}; var s = new WeakSet(); s.add(v); s.has(v)"));
        assertEquals(false, eval("var v = {}; new WeakSet().has(v)"));
        assertEquals(true, eval("var v = {}; var s = new WeakSet(); s.add(v); s.delete(v)"));
        assertEquals(false, eval("var v = {}; var s = new WeakSet(); s.add(v); s.delete(v); s.has(v)"));
        assertEquals(false, eval("var v = {}; new WeakSet().delete(v)"));
        assertEquals(false, eval("var s = new WeakSet(); s.add({}); s.has({})"));
    }

    @Test
    void weakSetAddIsChainable() {
        assertEquals(true, eval("var v = {}; var s = new WeakSet(); s.add(v) === s"));
        assertEquals(true, eval("var a = {}, b = {}; var s = new WeakSet(); s.add(a).add(b); s.has(b)"));
    }

    @Test
    void weakSetFromIterable() {
        assertEquals(true, eval("var a = {}, b = {}; new WeakSet([a, b]).has(b)"));
    }

    @Test
    void weakSetPrimitiveValueThrows() {
        assertEquals("TypeError: Invalid value used in weak set",
                eval("try { new WeakSet().add('a'); 'no-throw' } catch (e) { e.name + ': ' + e.message }"));
        assertEquals("TypeError", eval("try { new WeakSet().add(1); 'no-throw' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("try { new WeakSet([null]); 'no-throw' } catch (e) { e.name }"));
    }

    @Test
    void weakSetPrimitiveLookupsDoNotThrow() {
        assertEquals(false, eval("new WeakSet().has('a')"));
        assertEquals(false, eval("new WeakSet().delete(1)"));
    }

    @Test
    void weakSetHasNoSizeOrIteration() {
        assertEquals("undefined", eval("typeof new WeakSet().size"));
        assertEquals("undefined", eval("typeof new WeakSet().forEach"));
        assertEquals("undefined", eval("typeof new WeakSet().clear"));
        assertEquals("undefined", eval("typeof new WeakSet().values"));
        assertThrows(Exception.class, () -> eval("for (var v of new WeakSet()) {}"));
    }

    @Test
    void weakSetShape() {
        assertEquals("function", eval("typeof WeakSet"));
        assertEquals("object", eval("typeof new WeakSet()"));
        assertEquals(true, eval("new WeakSet() instanceof WeakSet"));
        assertEquals("[object WeakSet]", eval("Object.prototype.toString.call(new WeakSet())"));
    }

    @Test
    void weakSetWithoutNewThrows() {
        assertThrows(Exception.class, () -> eval("WeakSet()"));
    }

}
