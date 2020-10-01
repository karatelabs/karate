package com.intuit.karate.runtime;

import com.intuit.karate.graal.JsValue;
import com.intuit.karate.graal.JsEngine;
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
        Variable res = fun.invokeFunction(1, 2);
        assertEquals(3, res.<Number>getValue());
    }

}
