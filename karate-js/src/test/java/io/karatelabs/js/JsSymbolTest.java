package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class JsSymbolTest extends EvalBase {

    @Test
    void symbolTypeOf() {
        assertEquals("symbol", eval("typeof Symbol()"));
        assertEquals("symbol", eval("typeof Symbol('k')"));
        assertEquals("symbol", eval("typeof Symbol.iterator"));
        assertEquals("symbol", eval("typeof Symbol.toPrimitive"));
    }

    @Test
    void symbolToStringTag() {
        assertEquals("[object Symbol]", eval("Object.prototype.toString.call(Symbol('k'))"));
        assertEquals("[object Symbol]", eval("Object.prototype.toString.call(Symbol.iterator)"));
    }

    @Test
    void symbolIdentity() {
        assertEquals(true, eval("var s = Symbol('k'); s === s"));
        assertEquals(false, eval("Symbol('k') === Symbol('k')"));
        assertEquals(true, eval("Symbol.iterator === Symbol.iterator"));
    }

    @Test
    void symbolStringFormIsNotAJavaIdentity() {
        // a minted symbol has no string key at all; String(sym) is the spec's
        // SymbolDescriptiveString, never a Java toString
        assertEquals("Symbol(k)", eval("String(Symbol('k'))"));
        assertEquals(-1, eval("String(Symbol('k')).indexOf('io.karatelabs')"));
        // a well-known symbol IS its engine-internal string key
        assertEquals("@@iterator", eval("String(Symbol.iterator)"));
    }

    @Test
    void symbolKeyedPropertyRoundTrips() {
        assertEquals(1, eval("var s = Symbol('k'); var o = {}; o[s] = 1; o[s]"));
        assertEquals(1, eval("var s = Symbol('k'); var o = {[s]: 1}; o[s]"));
        // reads walk the prototype chain, writes stay own
        assertEquals(1, eval("var s = Symbol('k'); var p = {}; p[s] = 1; Object.create(p)[s]"));
        assertEquals(false, eval("var s = Symbol('k'); var p = {}; p[s] = 1;"
                + " Object.create(p).hasOwnProperty(s)"));
        assertEquals(true, eval("var s = Symbol('k'); var o = {}; o[s] = 1; s in o"));
        assertEquals(true, eval("var s = Symbol('k'); var o = {}; o[s] = 1; o.hasOwnProperty(s)"));
        assertEquals("undefined", eval("var s = Symbol('k'); var o = {}; o[s] = 1; delete o[s]; typeof o[s]"));
        // a symbol key is in the symbol partition, never the string one
        assertEquals(0, eval("var o = {}; o[Symbol('k')] = 1; o[Symbol('k')] = 2;"
                + " Object.getOwnPropertyNames(o).length"));
        assertEquals(2, eval("var o = {}; o[Symbol('k')] = 1; o[Symbol('k')] = 2;"
                + " Object.getOwnPropertySymbols(o).length"));
        // two symbols with the same description are distinct keys
        assertEquals(3, eval("var a = Symbol('k'), b = Symbol('k'); var o = {};"
                + " o[a] = 1; o[b] = 2; o[a] + o[b]"));
    }

    @Test
    void symbolKeysAreInvisibleToStringKeySurfaces() {
        assertEquals("[]", eval("var o = {}; o[Symbol('k')] = 1; JSON.stringify(Object.keys(o))"));
        assertEquals("[]", eval("var o = {}; o[Symbol('k')] = 1;"
                + " JSON.stringify(Object.getOwnPropertyNames(o))"));
        assertEquals("{}", eval("var o = {}; o[Symbol('k')] = 1; JSON.stringify(o)"));
        assertEquals("[]", eval("var o = {}; o[Symbol('k')] = 1;"
                + " var a = []; for (var k in o) a.push(k); JSON.stringify(a)"));
        // inherited symbol keys stay out of for-in too
        assertEquals("[]", eval("var p = {}; p[Symbol('k')] = 1; var o = Object.create(p);"
                + " var a = []; for (var k in o) a.push(k); JSON.stringify(a)"));
    }

    @Test
    void symbolValuesAreUnrepresentableInJson() {
        assertEquals("undefined", eval("typeof JSON.stringify(Symbol('x'))"));
        assertEquals("[null]", eval("JSON.stringify([Symbol('x')])"));
        assertEquals("{}", eval("JSON.stringify({a: Symbol('x')})"));
        assertEquals("{}", eval("JSON.stringify({a: 1}, function(k, v) {"
                + " return k === 'a' ? Symbol('x') : v })"));
    }

    @Test
    void symbolKeysCopyThroughSpreadAndAssign() {
        // spec CopyDataProperties copies both key partitions
        assertEquals(1, eval("var s = Symbol('k'); var o = {}; o[s] = 1; var c = {...o}; c[s]"));
        assertEquals(1, eval("var s = Symbol('k'); var o = {}; o[s] = 1;"
                + " var c = Object.assign({}, o); c[s]"));
        assertEquals(1, eval("var s = Symbol('k'); var o = {}; o[s] = 1;"
                + " Object.getOwnPropertySymbols({...o}).length"));
    }

    @Test
    void symbolKeyedClassMembers() {
        assertEquals(7, eval("var s = Symbol('m'); class C { [s]() { return 7 } } new C()[s]()"));
        assertEquals(7, eval("var s = Symbol('f'); class C { static [s] = 7 } C[s]"));
        // the method is in the symbol partition, not among the prototype's string keys
        assertEquals(0, eval("var s = Symbol('m'); class C { [s]() {} }"
                + " Object.getOwnPropertyNames(C.prototype).filter(function(k) {"
                + " return k !== 'constructor' }).length"));
    }

    @Test
    void reflectOwnKeysPartitionsStringsAndSymbols() {
        assertEquals(2, eval("var o = {a: 1}; o[Symbol('k')] = 2; Reflect.ownKeys(o).length"));
        assertEquals("a", eval("var o = {a: 1}; o[Symbol('k')] = 2; Reflect.ownKeys(o)[0]"));
        assertEquals("symbol", eval("var o = {a: 1}; o[Symbol('k')] = 2; typeof Reflect.ownKeys(o)[1]"));
    }

    @Test
    void wellKnownSymbolsStillIndexProperties() {
        assertEquals("function", eval("typeof [][Symbol.iterator]"));
        assertEquals(1, eval("var o = {}; o[Symbol.iterator] = function() {}; "
                + "Object.getOwnPropertyNames(o).length"));
    }

    @Test
    void symbolValuedNullIsAPresentValue() {
        // absence is the slot, never the value — a stored null must not read
        // through to the prototype, nor vanish when copied
        assertEquals("object", eval("var s = Symbol('x'); var o = {}; o[s] = null; typeof o[s]"));
        assertEquals("object", eval("var s = Symbol('x'); var p = {}; p[s] = 1;"
                + " var o = Object.create(p); o[s] = null; typeof o[s]"));
        assertEquals("object", eval("var s = Symbol('x'); var o = {}; o[s] = null;"
                + " typeof Object.assign({}, o)[s]"));
        assertEquals("object", eval("var s = Symbol('x'); var o = {}; o[s] = null;"
                + " typeof ({...o})[s]"));
    }

    @Test
    void symbolKeyedSuperAccess() {
        assertEquals(7, eval("var s = Symbol('x');"
                + " class A { [s]() { return 7 } }"
                + " class B extends A { m() { return super[s]() } }"
                + " new B().m()"));
        assertEquals(5, eval("var s = Symbol('x');"
                + " class A { constructor() { this.v = 5 } }"
                + " A.prototype[s] = function() { return this.v };"
                + " class B extends A { m() { return super[s]() } }"
                + " new B().m()"));
        assertEquals(3, eval("var s = Symbol('x');"
                + " class A {} A.prototype[s] = 3;"
                + " class B extends A { m() { return super[s] } }"
                + " new B().m()"));
    }

    @Test
    void objectHasOwnSeesSymbolKeys() {
        assertEquals(true, eval("var s = Symbol('x'); var o = {}; o[s] = 1; Object.hasOwn(o, s)"));
        assertEquals(false, eval("var s = Symbol('x'); Object.hasOwn({}, s)"));
    }

    @Test
    void objectFromEntriesKeepsSymbolKeys() {
        assertEquals(7, eval("var s = Symbol('x'); Object.fromEntries([[s, 7]])[s]"));
        assertEquals("[]", eval("var s = Symbol('x');"
                + " JSON.stringify(Object.getOwnPropertyNames(Object.fromEntries([[s, 7]])))"));
        assertEquals(1, eval("var s = Symbol('x');"
                + " Object.getOwnPropertySymbols(Object.fromEntries([[s, 7]])).length"));
    }

    @Test
    void symbolPropertyDescriptors() {
        assertEquals(3, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3}); o[s]"));
        assertEquals("[]", eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3});"
                + " JSON.stringify(Object.getOwnPropertyNames(o))"));
        assertEquals(1, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3});"
                + " Object.getOwnPropertySymbols(o).length"));
        assertEquals("object", eval("var s = Symbol('x'); var o = {}; o[s] = 1;"
                + " typeof Object.getOwnPropertyDescriptor(o, s)"));
        assertEquals(true, eval("var s = Symbol('x'); var o = {}; o[s] = 1;"
                + " Object.getOwnPropertyDescriptor(o, s).enumerable"));
        assertEquals(false, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3});"
                + " Object.getOwnPropertyDescriptor(o, s).enumerable"));
    }

    @Test
    void nonEnumerableSymbolIsNotCopied() {
        assertEquals("undefined", eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3});"
                + " typeof Object.assign({}, o)[s]"));
        assertEquals("undefined", eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3});"
                + " typeof ({...o})[s]"));
        assertEquals(3, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 3, enumerable: true});"
                + " Object.assign({}, o)[s]"));
    }

    @Test
    void symbolKeyedAccessorsAndFreeze() {
        assertEquals(9, eval("var s = Symbol('x');"
                + " class C { get [s]() { return 9 } } new C()[s]"));
        assertEquals(4, eval("var s = Symbol('x'); var o = {get [s]() { return 4 }}; o[s]"));
        // freeze blocks a symbol-keyed write, as it does a string-keyed one
        assertEquals(1, eval("var s = Symbol('x'); var o = {}; o[s] = 1;"
                + " Object.freeze(o); o[s] = 2; o[s]"));
        assertEquals("undefined", eval("var s = Symbol('x'); var o = Object.freeze({});"
                + " o[s] = 1; typeof o[s]"));
    }

    @Test
    void implicitSymbolCoercionThrows() {
        assertEquals("TypeError", eval("var s = Symbol('x');"
                + " try { s + '' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("var s = Symbol('x');"
                + " try { '' + s } catch (e) { e.name }"));
        assertEquals("TypeError", eval("var s = Symbol('x');"
                + " try { `${s}` } catch (e) { e.name }"));
        // String(sym) is the exceptional operation and still works
        assertEquals("Symbol(x)", eval("String(Symbol('x'))"));
        // a symbol equals only itself — comparison must not coerce
        assertEquals(false, eval("Symbol('x') == 'Symbol(x)'"));
    }

    @Test
    void symbolSetConsultsThePrototypeChain() {
        // an inherited setter runs with the receiver, and creates no own key
        assertEquals("[9,false]", eval("var s = Symbol('x'); var p = {};"
                + " Object.defineProperty(p, s, {set: function(v) { this.hit = v }});"
                + " var o = Object.create(p); o[s] = 9;"
                + " JSON.stringify([o.hit, Object.hasOwn(o, s)])"));
        // an inherited non-writable data property rejects the write
        assertEquals("[1,false]", eval("var s = Symbol('x'); var p = {};"
                + " Object.defineProperty(p, s, {value: 1, writable: false});"
                + " var o = Object.create(p); o[s] = 9;"
                + " JSON.stringify([o[s], Object.hasOwn(o, s)])"));
        // an inherited writable data property is shadowed by an own slot
        assertEquals("[9,1,true]", eval("var s = Symbol('x'); var p = {};"
                + " Object.defineProperty(p, s, {value: 1, writable: true});"
                + " var o = Object.create(p); o[s] = 9;"
                + " JSON.stringify([o[s], p[s], Object.hasOwn(o, s)])"));
        // Object.assign uses [[Set]], so a setter on the TARGET runs
        assertEquals("[9,false]", eval("var s = Symbol('x'); var p = {};"
                + " Object.defineProperty(p, s, {set: function(v) { this.hit = v }});"
                + " var target = Object.create(p); var src = {}; src[s] = 9;"
                + " Object.assign(target, src);"
                + " JSON.stringify([target.hit, Object.hasOwn(target, s)])"));
    }

    @Test
    void symbolDescriptorRedefinition() {
        // same-value redefine of a non-configurable slot is a legal no-op
        assertEquals(1, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1});"
                + " Object.defineProperty(o, s, {value: 1}); o[s]"));
        // writable true -> false narrowing is legal
        assertEquals(false, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1, writable: true});"
                + " Object.defineProperty(o, s, {writable: false});"
                + " Object.getOwnPropertyDescriptor(o, s).writable"));
        // redefining the same value after freeze succeeds
        assertEquals(1, eval("var s = Symbol('x'); var o = {}; o[s] = 1; Object.freeze(o);"
                + " Object.defineProperty(o, s, {value: 1}); o[s]"));
        // a real change to a non-configurable slot throws
        assertEquals("TypeError", eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1});"
                + " try { Object.defineProperty(o, s, {value: 2}) } catch (e) { e.name }"));
        assertEquals("TypeError", eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1});"
                + " try { Object.defineProperty(o, s, {configurable: true}) } catch (e) { e.name }"));
    }

    @Test
    void symbolKeyedClassMethodIsNonEnumerable() {
        assertEquals(false, eval("var s = Symbol('m'); class C { [s]() {} }"
                + " Object.getOwnPropertyDescriptor(C.prototype, s).enumerable"));
        assertEquals("undefined", eval("var s = Symbol('m'); class C { [s]() {} }"
                + " typeof Object.assign({}, C.prototype)[s]"));
        assertEquals("undefined", eval("var s = Symbol('m'); class C { [s]() {} }"
                + " typeof ({...C.prototype})[s]"));
    }

    @Test
    void symbolWriteAndDeleteRespectStrictMode() {
        assertEquals("TypeError", eval("'use strict'; var s = Symbol('x'); var o = {};"
                + " o[s] = 1; Object.freeze(o);"
                + " try { o[s] = 2; 'no' } catch (e) { e.name }"));
        assertEquals("no", eval("var s = Symbol('x'); var o = {};"
                + " o[s] = 1; Object.freeze(o);"
                + " try { o[s] = 2; 'no' } catch (e) { e.name }"));
        assertEquals("TypeError", eval("'use strict'; var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1});"
                + " try { delete o[s]; 'no' } catch (e) { e.name }"));
        assertEquals(false, eval("var s = Symbol('x'); var o = {};"
                + " Object.defineProperty(o, s, {value: 1}); delete o[s]"));
    }

    @Test
    void reflectOwnKeysUsesOrdinaryOrdering() {
        // integer-like keys ascending first, then strings in insertion order
        assertEquals("[\"1\",\"2\",\"a\"]", eval("var o = {};"
                + " o['2'] = 2; o['1'] = 1; o.a = 3;"
                + " JSON.stringify(Reflect.ownKeys(o))"));
        assertEquals("symbol", eval("var o = {}; o['2'] = 2; o.a = 3; o[Symbol('k')] = 1;"
                + " typeof Reflect.ownKeys(o)[Reflect.ownKeys(o).length - 1]"));
    }

    @Test
    void symbolLooseEquality() {
        assertEquals(true, eval("var s = Symbol('x'); s == s"));
        assertEquals(true, eval("var s = Symbol('x'); s == Object(s)"));
        assertEquals(false, eval("var s = Symbol('x'); s == null"));
        assertEquals(false, eval("var s = Symbol('x'); s == undefined"));
        assertEquals(false, eval("Symbol('x') == Symbol('x')"));
    }

    @Test
    void wellKnownIdentityIsPerEngine() {
        // Deliberate deviation, not spec: the spec shares well-known symbols
        // across realms within an agent, but each Engine mints its own set.
        // Within one Engine identity holds (see symbolIdentity).
        Engine one = new Engine();
        Engine two = new Engine();
        assertEquals(true, one.eval("Symbol.iterator === Symbol.iterator"));
        assertNotSame(one.eval("Symbol.iterator"), two.eval("Symbol.iterator"));
    }

    @Test
    void symbolGetterRunsWhenCopied() {
        // spec: Get(source, key) then CreateDataProperty / Set — the getter runs
        // exactly once, with the source as `this`, and its RESULT is copied
        assertEquals("[7,1]", eval("var s = Symbol('x'); var n = 0;"
                + " var o = {get [s]() { n++; return 7 }};"
                + " var a = Object.assign({}, o); JSON.stringify([a[s], n])"));
        assertEquals("[7,1]", eval("var s = Symbol('x'); var n = 0;"
                + " var o = {get [s]() { n++; return 7 }};"
                + " var a = {...o}; JSON.stringify([a[s], n])"));
        // the copy is a data property, not the accessor
        assertEquals("undefined", eval("var s = Symbol('x'); var o = {get [s]() { return 7 }};"
                + " typeof Object.getOwnPropertyDescriptor(Object.assign({}, o), s).get"));
        assertEquals(7, eval("var s = Symbol('x'); var o = {get [s]() { return 7 }};"
                + " Object.getOwnPropertyDescriptor(Object.assign({}, o), s).value"));
        assertEquals(7, eval("var s = Symbol('x'); var o = {get [s]() { return this.v }, v: 7};"
                + " Object.assign({}, o)[s]"));
        // a throwing getter propagates
        assertEquals("boom", eval("var s = Symbol('x');"
                + " var o = {get [s]() { throw new Error('boom') }};"
                + " try { Object.assign({}, o) } catch (e) { e.message }"));
        assertEquals("boom", eval("var s = Symbol('x');"
                + " var o = {get [s]() { throw new Error('boom') }};"
                + " try { ({...o}) } catch (e) { e.message }"));
    }

    @Test
    void symbolCopyStopsAtAThrowingGetter() {
        String src = "var a = Symbol('a'), b = Symbol('b'); var n = 0; var o = {};"
                + " Object.defineProperty(o, a, {enumerable: true,"
                + "   get: function() { throw new Error('boom') }});"
                + " Object.defineProperty(o, b, {enumerable: true,"
                + "   get: function() { n++; return 1 }});";
        // assign: the second getter never runs and nothing is written past the throw
        assertEquals("[0,0,\"boom\"]", eval(src
                + " var t = {}; var msg = '';"
                + " try { Object.assign(t, o) } catch (e) { msg = e.message }"
                + " JSON.stringify([n, Object.getOwnPropertySymbols(t).length, msg])"));
        // spread: same abrupt completion, and the error propagates
        assertEquals("[0,\"boom\"]", eval(src
                + " var msg = '';"
                + " try { var c = {...o} } catch (e) { msg = e.message }"
                + " JSON.stringify([n, msg])"));
    }

}
