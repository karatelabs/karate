package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class TermsKindTest extends EvalBase {

    @Test
    void testPlainObject() {
        Object o = eval("({ a: 1 })");
        assertTrue(Terms.isPlainObject(o));
        assertFalse(Terms.isPlainArray(o));
        assertFalse(Terms.isSymbol(o));
    }

    @Test
    void testPlainObjectWithPrototype() {
        // an instance of a user constructor is still a plain object
        Object o = eval("function F() { this.a = 1; }; new F()");
        assertTrue(Terms.isPlainObject(o));
        assertFalse(Terms.isPlainArray(o));
        assertFalse(Terms.isSymbol(o));
        Object c = eval("class C { constructor() { this.a = 1; } }; new C()");
        assertTrue(Terms.isPlainObject(c));
        Object p = eval("Object.create({ a: 1 })");
        assertTrue(Terms.isPlainObject(p));
    }

    @Test
    void testPlainArray() {
        Object a = eval("[1, 2]");
        assertTrue(Terms.isPlainArray(a));
        assertFalse(Terms.isPlainObject(a));
        assertFalse(Terms.isSymbol(a));
        Object empty = eval("[]");
        assertTrue(Terms.isPlainArray(empty));
        assertFalse(Terms.isPlainObject(empty));
    }

    @Test
    void testSymbol() {
        Object s = eval("Symbol('x')");
        assertTrue(Terms.isSymbol(s));
        assertFalse(Terms.isPlainObject(s));
        assertFalse(Terms.isPlainArray(s));
        Object w = eval("Symbol.iterator");
        assertTrue(Terms.isSymbol(w));
        assertFalse(Terms.isPlainObject(w));
    }

    @Test
    void testFunctionsAreNeither() {
        for (String expr : new String[]{"(function f() {})", "(() => 1)", "Math.max", "Date", "class C {}"}) {
            Object f = eval(expr);
            assertFalse(Terms.isPlainObject(f), expr);
            assertFalse(Terms.isPlainArray(f), expr);
            assertFalse(Terms.isSymbol(f), expr);
        }
    }

    @Test
    void testBuiltinObjectSubtypesAreNotPlain() {
        // JsMap / JsSet / JsRegex / JsError all extend JsObject and none of them
        // self-reports as a function — "plain" has to mean exactly {}
        for (String expr : new String[]{"new Map()", "new Set()", "/x/", "new Error('e')",
                "class M extends Map {}; new M()"}) {
            Object v = eval(expr);
            assertFalse(Terms.isPlainObject(v), expr);
            assertFalse(Terms.isPlainArray(v), expr);
            assertFalse(Terms.isSymbol(v), expr);
        }
    }

    @Test
    void testTypedArrayIsNotAPlainArray() {
        // a Uint8Array reaches an embedder as a raw byte[] — it never was a JsArray
        // on that path, but JsUint8Array extends JsArray, so pin the engine value too
        Object host = eval("new Uint8Array(2)");
        assertInstanceOf(byte[].class, host);
        assertFalse(Terms.isPlainArray(host));
        assertFalse(Terms.isPlainObject(host));
        Object typed = new JsUint8Array(new byte[2]);
        assertFalse(Terms.isPlainArray(typed));
        assertFalse(Terms.isPlainObject(typed));
        assertFalse(Terms.isSymbol(typed));
    }

    @Test
    void testHostValuesAreNeither() {
        for (Object v : new Object[]{null, new HashMap<>(), new ArrayList<>(), "foo", 42, true, new DemoPojo(),
                Terms.UNDEFINED}) {
            assertFalse(Terms.isPlainObject(v));
            assertFalse(Terms.isPlainArray(v));
            assertFalse(Terms.isSymbol(v));
        }
    }

}
