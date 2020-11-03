package com.intuit.karate.runtime;

import com.intuit.karate.graal.JsValue;
import com.intuit.karate.graal.JsEngine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
public class VariableTest {

    static final Logger logger = LoggerFactory.getLogger(VariableTest.class);

    JsEngine je;

    @BeforeEach
    void beforeEach() {
        je = JsEngine.global();
    }

    @AfterEach
    void afterEach() {
        JsEngine.remove();
    }

    @Test
    void testFunction() {
        JsValue jv = je.eval("(function(a, b){ return a + b })");
        Variable fun = new Variable(jv);
        assertTrue(fun.isFunction());
        Variable res = fun.invokeFunction(je, 1, 2);
        assertEquals(3, res.<Number>getValue());
    }

    @Test
    void testPojo() {
        JsValue jv = je.eval("new com.intuit.karate.runtime.SimplePojo()");
        assertTrue(jv.isOther());
    }

    @Test
    void testClass() {
        JsValue jv = je.eval("Java.type('com.intuit.karate.runtime.MockUtils')");
        assertTrue(jv.isOther());
        Variable v = new Variable(jv);
        assertEquals(v.type, Variable.Type.OTHER);
        assertTrue(v.getValue() instanceof Value);
    }

}
