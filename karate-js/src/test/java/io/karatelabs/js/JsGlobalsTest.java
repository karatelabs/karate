package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsGlobalsTest extends EvalBase {

    @Test
    void testStructuredCloneIsAFunction() {
        assertEquals("function", eval("typeof structuredClone"));
        assertEquals(1, eval("structuredClone.length"));
        assertEquals("structuredClone", eval("structuredClone.name"));
    }

    @Test
    void testStructuredClonePrimitives() {
        assertEquals(5, eval("structuredClone(5)"));
        assertEquals("foo", eval("structuredClone('foo')"));
        assertEquals(true, eval("structuredClone(true)"));
        assertEquals(true, eval("structuredClone(null) === null"));
        assertEquals(true, eval("structuredClone(undefined) === undefined"));
        assertEquals(true, eval("isNaN(structuredClone(NaN))"));
    }

    @Test
    void testStructuredCloneObject() {
        matchEval("structuredClone({ a: 1, b: { c: [2, 3] } })", "{ a: 1, b: { c: [2, 3] } }");
        assertEquals(false, eval("const o = { a: 1 }; structuredClone(o) === o"));
        assertEquals(false, eval("const o = { a: { b: 1 } }; structuredClone(o).a === o.a"));
        // non-enumerable and inherited properties do not survive, per spec
        assertEquals(true, eval("const o = Object.create({ p: 1 }); o.q = 2;"
                + " const c = structuredClone(o); c.q === 2 && c.p === undefined"));
    }

    @Test
    void testStructuredCloneArray() {
        matchEval("structuredClone([1, 'a', [2]])", "[1, 'a', [2]]");
        assertEquals(false, eval("const a = [1]; structuredClone(a) === a"));
        assertEquals(true, eval("Array.isArray(structuredClone([1, 2]))"));
    }

    @Test
    void testStructuredCloneArrayOwnProperties() {
        assertEquals(true, eval("const a = [0]; a.extra = { n: 1 }; const c = structuredClone(a);"
                + " c.extra.n === 1 && c.extra !== a.extra"));
        assertEquals(true, eval("const a = [0]; a.self = a; const c = structuredClone(a);"
                + " c.self === c && c[0] === 0"));
        assertEquals(2, eval("const a = [0];"
                + " Object.defineProperty(a, '0', { enumerable: true, configurable: true,"
                + " get() { return { n: 2 } } });"
                + " structuredClone(a)[0].n"));
    }

    @Test
    void testStructuredCloneSparseArray() {
        assertEquals(true, eval("const a = [0, , 2]; const c = structuredClone(a);"
                + " c.length === 3 && (1 in c) === false && c[0] === 0 && c[2] === 2"));
        // a trailing hole carries no own key, so only length preserves it
        assertEquals(true, eval("const a = [0]; a.length = 4; const c = structuredClone(a);"
                + " c.length === 4 && (3 in c) === false"));
    }

    @Test
    void testStructuredCloneDate() {
        assertEquals(true, eval("const d = new Date(1700000000000); const c = structuredClone({ d: d }).d;"
                + " c instanceof Date && c !== d && c.getTime() === 1700000000000"));
    }

    @Test
    void testStructuredCloneMapAndSet() {
        assertEquals(true, eval("const m = new Map([['k', { n: 1 }]]); const c = structuredClone(m);"
                + " c instanceof Map && c !== m && c.size === 1"
                + " && c.get('k').n === 1 && c.get('k') !== m.get('k')"));
        assertEquals(true, eval("const s = new Set([1, 'two']); const c = structuredClone({ s: s }).s;"
                + " c instanceof Set && c !== s && c.size === 2 && c.has(1) && c.has('two')"));
        // an object element keeps its identity relative to the rest of the clone
        assertEquals(true, eval("const o = { x: 1 }; const m = new Map([['a', o], ['b', o]]);"
                + " const c = structuredClone(m); c.get('a') === c.get('b') && c.get('a') !== o"));
        // the same source object used as both key and value stays one object
        assertEquals(true, eval("const o = { x: 1 }; const m = new Map([[o, o]]);"
                + " const c = structuredClone(m); const k = [...c.keys()][0];"
                + " k !== o && c.get(k) === k"));
        assertEquals(true, eval("const m = new Map(); const a = [m]; m.set('back', a);"
                + " const c = structuredClone(m); c.get('back')[0] === c"));
    }

    @Test
    void testStructuredCloneRegExp() {
        assertEquals(true, eval("const r = /ab+c/gi; const c = structuredClone({ r: r }).r;"
                + " c instanceof RegExp && c !== r && c.source === 'ab+c' && c.flags === 'gi'"
                + " && c.test('xxABBBC')"));
    }

    @Test
    void testStructuredCloneError() {
        assertEquals(true, eval("const e = new TypeError('boom'); const c = structuredClone(e);"
                + " c instanceof TypeError && c !== e && c.name === 'TypeError' && c.message === 'boom'"));
        // a user subclass clones onto the nearest built-in Error prototype, so
        // `instanceof AppError` is lost by design while `instanceof Error` holds
        assertEquals(true, eval("class AppError extends Error {}"
                + " const e = new AppError('boom'); const c = structuredClone(e);"
                + " c instanceof Error && !(c instanceof AppError) && c.message === 'boom'"));
        assertEquals(true, eval("class AppError extends Error { constructor(m, code) { super(m);"
                + " this.code = code } } const c = structuredClone(new AppError('boom', 42));"
                + " c instanceof Error && c.code === 42 && c.message === 'boom'"));
    }

    @Test
    void testStructuredCloneErrorMessageIsCoerced() {
        assertEquals(true, eval("const e = new Error(); e.message = 'plain';"
                + " structuredClone(e).message === 'plain'"));
        // per spec the message is ToString'd, so no source reference can leak in
        assertEquals(true, eval("const e = new Error(); e.message = { x: 1 };"
                + " const c = structuredClone(e);"
                + " c.message === '[object Object]' && c.message !== e.message"));
        assertEquals(true, eval("const e = new Error(); e.message = e;"
                + " const c = structuredClone(e);"
                + " typeof c.message === 'string' && c.message !== e"));
        // an accessor message is not a data property, so the default survives
        assertEquals("", eval("const e = new Error();"
                + " Object.defineProperty(e, 'message', { configurable: true, get() { return { x: 1 } } });"
                + " structuredClone(e).message"));
        // the descriptor is inspected, never Get — an enumerable getter must not fire
        assertEquals(true, eval("let calls = 0; const e = new Error();"
                + " Object.defineProperty(e, 'message',"
                + " { configurable: true, enumerable: true, get() { calls++; return 'x' } });"
                + " const c = structuredClone(e); calls === 0 && c.message === ''"));
        assertEquals("", eval("const e = new Error();"
                + " Object.defineProperty(e, 'message',"
                + " { configurable: true, enumerable: true, get() { throw new Error('nope') } });"
                + " structuredClone(e).message"));
        // a getter that deletes a later key mid-clone must not leave a phantom
        // own property on the clone
        assertEquals(true, eval("const e = new Error();"
                + " Object.defineProperty(e, 'a',"
                + " { enumerable: true, configurable: true, get() { delete e.b; return 1 } });"
                + " e.b = 2; const c = structuredClone(e);"
                + " c.a === 1 && !Object.hasOwn(c, 'b')"));
    }

    @Test
    void testStructuredCloneCycle() {
        assertEquals(true, eval("const a = { n: 1 }; a.self = a; const c = structuredClone(a);"
                + " c !== a && c.self === c && c.n === 1"));
        assertEquals(true, eval("const a = []; a.push(a); const c = structuredClone(a);"
                + " c !== a && c[0] === c"));
        assertEquals(true, eval("const a = { name: 'a' }; const b = { name: 'b', a: a }; a.b = b;"
                + " const c = structuredClone(a); c.b.a === c && c.b !== b"));
    }

    @Test
    void testStructuredCloneRejectsFunctionAndSymbol() {
        assertEquals("DataCloneError", eval("try { structuredClone(function f() {}); 'no throw' }"
                + " catch (e) { e.name }"));
        assertEquals("DataCloneError", eval("try { structuredClone({ fn: () => 1 }); 'no throw' }"
                + " catch (e) { e.name }"));
        assertEquals("DataCloneError", eval("try { structuredClone(Symbol()); 'no throw' }"
                + " catch (e) { e.name }"));
        assertEquals(true, eval("try { structuredClone(function f() {}); false }"
                + " catch (e) { e instanceof Error }"));
    }

    @Test
    void testStructuredCloneIgnoresTransferOption() {
        matchEval("structuredClone({ a: 1 }, { transfer: [] })", "{ a: 1 }");
    }

    @Test
    void testPerformanceNow() {
        assertEquals("object", eval("typeof performance"));
        assertEquals("function", eval("typeof performance.now"));
        assertEquals(true, eval("performance.now() >= 0"));
        assertEquals(true, eval("const a = performance.now(); let x = 0;"
                + " for (let i = 0; i < 200000; i++) x += i;"
                + " const b = performance.now(); b > a && x > 0"));
        assertEquals(true, eval("performance.timeOrigin > 0"));
    }

    @Test
    void testBuiltinsEnumeratesGlobalsAndMembers() {
        Map<String, List<String>> builtins = Engine.builtins();
        assertTrue(builtins.keySet().containsAll(List.of("parseInt", "undefined", "globalThis", "setTimeout")));
        assertTrue(builtins.get("Math").containsAll(List.of("PI", "max", "floor")));
        assertTrue(builtins.get("JSON").containsAll(List.of("parse", "stringify")));
        assertTrue(builtins.get("Object").containsAll(List.of("keys", "assign", "prototype")));
        assertTrue(builtins.get("Promise").containsAll(List.of("all", "resolve")));
        assertTrue(builtins.get("console").containsAll(List.of("log", "info", "debug", "warn", "error")));
        assertTrue(builtins.get("Java").containsAll(List.of("type", "to")));
        assertTrue(builtins.get("String.prototype").containsAll(List.of("indexOf", "padStart", "trim")));
        assertTrue(builtins.get("Array.prototype").containsAll(List.of("map", "filter", "includes")));
        assertTrue(builtins.get("Object.prototype").contains("hasOwnProperty"));
        assertTrue(builtins.get("Promise.prototype").containsAll(List.of("then", "catch", "finally")));
        assertEquals(List.of(), builtins.get("NaN"));
        assertEquals(List.copyOf(builtins.keySet().stream().filter(k -> !k.contains(".")).toList()),
                builtins.get("globalThis"));
        assertFalse(builtins.get("Array.prototype").contains("@@iterator"));
        Engine engine = new Engine();
        engine.setExternalBridge(new ExternalBridge() {
        });
        builtins.forEach((owner, names) -> {
            if (!owner.equals("undefined")) {
                assertNotEquals("undefined", engine.eval("typeof " + owner), owner);
            }
            for (String name : names) {
                assertEquals(true, engine.eval("'" + name + "' in Object(" + owner + ")"), owner + "." + name);
            }
        });
        assertThrows(UnsupportedOperationException.class, () -> builtins.put("x", List.of()));
    }


    @Test
    void testCrypto() {
        assertEquals(true, eval("var a = new Uint8Array(16); crypto.getRandomValues(a) === a"));
        assertEquals(true, eval("var a = new Uint8Array(32); crypto.getRandomValues(a); a.some(function(b) { return b !== 0 })"));
        assertEquals(true, eval("/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(crypto.randomUUID())"));
        assertEquals("function", eval("typeof crypto.getRandomValues.bind(crypto)"));
    }

    @Test
    void testUint8ArraySet() {
        assertEquals("1,2,0,0", eval("var a = new Uint8Array(4); a.set([1, 2]); Array.prototype.join.call(a)"));
        assertEquals("0,1,2,0", eval("var a = new Uint8Array(4); a.set([1, 2], 1); Array.prototype.join.call(a)"));
        assertEquals("0,9,8,0", eval("var a = new Uint8Array(4); a.set(new Uint8Array([9, 8]), 1); Array.prototype.join.call(a)"));
        assertEquals("255,1", eval("var a = new Uint8Array(2); a.set([-1, 257]); Array.prototype.join.call(a)"));
        assertThrows(EngineException.class, () -> eval("new Uint8Array(2).set([1, 2, 3])"));
        assertThrows(EngineException.class, () -> eval("new Uint8Array(2).set([1], 3)"));
    }

    @Test
    void testUint32Array() {
        assertEquals(4294967295L, eval("var u = new Uint32Array(1); u[0] = -1; u[0]"));
        assertEquals(-1, eval("var u = new Uint32Array(1); u[0] = -1; u[0] | 0"));
        assertEquals(2147483648L, eval("var u = new Uint32Array(1); u[0] |= 0x80000000; u[0]"));
        assertEquals(3, eval("new Uint32Array(3).length"));
        assertEquals("1,2", eval("Array.prototype.join.call(new Uint32Array([1, 2.9]))"));
        assertEquals(false, eval("Array.isArray(new Uint32Array(1))"));
        assertEquals(true, eval("new Uint32Array(1) instanceof Uint32Array"));
        assertEquals(false, eval("new Uint32Array(1) instanceof Array"));
        assertThrows(EngineException.class, () -> eval("new Uint32Array(-1)"));
    }

    @Test
    void testSetInterval() {
        assertEquals("[\"x0\",\"x1\",\"x2\",\"t\"]", evalThen("var log = []; var n = 0;"
                + " var id = setInterval(function (a) { log.push(a + n); if (++n === 3) clearInterval(id) }, 5, 'x');"
                + " setTimeout(function () { log.push('t') }, 50); globalThis.L = log"));
        assertEquals("[]", evalThen("var log = []; var i = setInterval(() => log.push('no'), 1); clearInterval(i); globalThis.L = log"));
        assertEquals("[\"i\",\"i\",\"i\"]", evalThen("var log = [];"
                + " var k = setInterval(() => { log.push('i'); if (log.length > 2) clearTimeout(k) }, 1); globalThis.L = log"));
    }

    @Test
    void testQueueMicrotask() {
        assertEquals("function", eval("typeof queueMicrotask"));
        assertEquals("[\"sync\",\"m1\",\"p\",\"m2\",\"t\"]", evalThen("var log = []; setTimeout(() => log.push('t'), 0);"
                + " queueMicrotask(() => log.push('m1')); Promise.resolve().then(() => log.push('p'));"
                + " queueMicrotask(() => log.push('m2')); log.push('sync'); globalThis.L = log"));
        assertEquals("TypeError", eval("try { queueMicrotask(1) } catch (e) { e.name }"));
    }

    @Test
    void testSetImmediate() {
        assertEquals("[\"sync\",\"micro\",3]", evalThen("var log = []; setImmediate(function (a, b) { log.push(a + b) }, 1, 2);"
                + " Promise.resolve().then(() => log.push('micro')); log.push('sync'); globalThis.L = log"));
        assertEquals("[\"yes\"]", evalThen("var log = []; var i = setImmediate(() => log.push('no')); clearImmediate(i);"
                + " setTimeout(() => log.push('yes'), 1); globalThis.L = log"));
    }

    @Test
    void testClearTimeoutAfterTheTimerFired() {
        // the timer is due before clearTimeout runs: its queued callback must not run
        assertEquals("[\"yes\"]", evalThen("var log = []; var i = setTimeout(() => log.push('no'), 0);"
                + " for (var w = Date.now(); Date.now() - w < 20;); clearTimeout(i);"
                + " setTimeout(() => log.push('yes'), 1); globalThis.L = log"));
    }

    @Test
    void testNamedFunctionExpressionOutlivesItsEnclosingCall() {
        assertEquals("[5,\"function\"]", evalThen("var log = []; (function () { const bar = 5, main = 1;"
                + " setTimeout(function main() { log.push(bar, typeof main) }, 1) })(); globalThis.L = log"));
        assertEquals("[\"foo\",\"bar\"]", evalThen("var log = []; (function () {"
                + " const foo = () => new Promise(r => { log.push('foo'); setTimeout(r, 5) }); const bar = () => log.push('bar');"
                + " (async function main() { await foo(); bar() })() })(); globalThis.L = log"));
    }

    /** Runs {@code text} to the end of its timers, then answers {@code JSON.stringify(L)}. */
    private Object evalThen(String text) {
        eval(text);
        return engine.eval("JSON.stringify(L)");
    }

    @Test
    void testEncodeUriWithoutArgument() {
        assertEquals("undefined", eval("encodeURIComponent()"));
        assertEquals("undefined", eval("encodeURI()"));
    }

    @Test
    void testDynamicImportIsATypeError() {
        assertEquals("TypeError: A dynamic import callback was not specified.",
                eval("try { import('x') } catch (e) { e.name + ': ' + e.message }"));
    }

}
