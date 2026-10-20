package io.karatelabs.js;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Context#getLocalVariables()} and {@link Context#evalWithLocals(String)}, read at a
 * statement the way a debugger does (from the interceptor, on the executing thread). Every case
 * runs with slot frames on and off, and calls its function more than once so slot frames engage.
 */
class ContextLocalVariablesTest {

    record Point(int line, Context context) {
    }

    /** The value of {@code read} at the last time the statement on the line marked {@code // @here} ran. */
    static Object at(boolean slotFrames, String script, Function<Context, Object> read) {
        boolean prev = SlotTable.ENABLED;
        SlotTable.ENABLED = slotFrames;
        try {
            List<String> lines = List.of(script.split("\n", -1));
            int marked = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("// @here")) {
                    marked = i;
                }
            }
            assertTrue(marked >= 0, "no // @here");
            int target = marked;
            Object[] result = new Object[1];
            Engine engine = new Engine();
            engine.setDebugSupport(new RunInterceptor<Point>() {
                @Override
                public Action beforeExecute(Point point) {
                    if (point.line() == target) {
                        result[0] = read.apply(point.context());
                    }
                    return Action.PROCEED;
                }
            }, (type, line, sourcePath, source, jsContext) -> new Point(line, (Context) jsContext));
            engine.eval(script);
            assertNotNull(result[0], "the marked statement did not run");
            return result[0];
        } finally {
            SlotTable.ENABLED = prev;
        }
    }

    static Map<String, Object> locals(boolean slotFrames, String script) {
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) at(slotFrames, script, Context::getLocalVariables);
        return vars;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void paramsVarLetConstAndNoGlobals(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                var g = 'global';
                function f(a, b) {
                  var v = a + b;
                  let l = v * 2;
                  const c = l + 1;
                  return c; // @here
                }
                f(1, 2); f(1, 2); f(1, 2);
                """);
        assertEquals(Map.of("a", 1, "b", 2, "v", 3, "l", 6, "c", 7), vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aBlockLetIsGoneOnceTheBlockIsLeft(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                function g() {
                  { let secret = 7; secret; }
                  var after = 1;
                  return after; // @here
                }
                g(); g(); g();
                """);
        assertFalse(vars.containsKey("secret"), vars::toString);
        assertEquals(1, vars.get("after"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aBlockLetIsThereInsideTheBlock(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                function h(n) {
                  for (let i = 0; i < n; i++) {
                    let inner = i * 10;
                    inner; // @here
                  }
                  return n;
                }
                h(2); h(2); h(2);
                """);
        assertEquals(Map.of("n", 2, "i", 1, "inner", 10), vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void closureVariablesAndShadowing(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                function outer() {
                  var o = 10;
                  var s = 'outer';
                  return function(x) {
                    let s = 'inner';
                    return x + o + s; // @here
                  };
                }
                var fn = outer();
                fn(1); fn(1); fn(1);
                """);
        assertEquals(1, vars.get("x"));
        assertEquals(10, vars.get("o"));
        assertEquals("inner", vars.get("s"));
        assertFalse(vars.containsKey("fn"), "a global: " + vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aNameInItsTdzIsLeftOutAndHidesTheOuterOne(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                function t() {
                  var s = 1;
                  {
                    var seen;
                    try { seen = s; } catch (e) { seen = 'tdz'; }
                    seen; // @here
                    let s = 2;
                    return s;
                  }
                }
                t(); t(); t();
                """);
        assertEquals("tdz", vars.get("seen"), "reading s before its let throws (spec TDZ)");
        assertFalse(vars.containsKey("s"), "in its TDZ, and the outer s is shadowed: " + vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFunctionLevelLetBeforeItsDeclaration(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                function t(n) {
                  n; // @here
                  let late = 2;
                  return late + n;
                }
                t(1); t(1); t(1);
                """);
        assertEquals(Map.of("n", 1), vars, "late is in its TDZ");
        assertEquals("ReferenceError", at(slotFrames, """
                function t(n) {
                  n; // @here
                  let late = 2;
                  return late + n;
                }
                t(1); t(1); t(1);
                """, c -> {
            try {
                c.evalWithLocals("late");
                return "no error";
            } catch (Exception e) {
                return e.getMessage().contains("late") ? "ReferenceError" : e.getMessage();
            }
        }), "an evaluation cannot see past the TDZ either");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aTopLevelBlock(boolean slotFrames) {
        Map<String, Object> vars = locals(slotFrames, """
                var g = 1;
                {
                  let top = 3;
                  top; // @here
                }
                """);
        assertEquals(Map.of("top", 3), vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void evalWithLocalsSeesTheLocalsAsJsValues(boolean slotFrames) {
        String script = """
                var g = 100;
                function f(a) {
                  var u;
                  var fn = function() { return a; };
                  var fn2 = fn;
                  return a; // @here
                }
                f(5); f(5); f(5);
                """;
        assertEquals(105, at(slotFrames, script, c -> c.evalWithLocals("a + g")));
        assertEquals(true, at(slotFrames, script, c -> c.evalWithLocals("u === undefined")));
        assertEquals(true, at(slotFrames, script, c -> c.evalWithLocals("fn === fn2")));
        assertEquals(5, at(slotFrames, script, c -> c.evalWithLocals("fn()")));
        // an assignment changes only the evaluation's copy
        assertEquals(5, at(slotFrames, script, c -> {
            c.evalWithLocals("a = 99");
            return c.getLocalVariables().get("a");
        }));
    }

}
