package com.intuit.karate.core;

import com.intuit.karate.js.JsEngine;
import java.util.function.BiFunction;
import java.util.function.Function;
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
    void testJsFunction() {
        Object jv = je.eval("(function(a, b){ return a + b })");
        Variable var = new Variable(jv);
        assertTrue(var.isJsFunction());
        assertFalse(var.isJavaFunction());
    }
    
    public String simpleFunction(String arg) {
        return arg;
    }
    
    public String simpleBiFunction(String arg1, String arg2) {
        return arg1 + arg2;
    }    

    @Test
    void testJavaFunction() {
        Variable v = new Variable((Function<String, String>) this::simpleFunction);
        assertTrue(v.isJavaFunction());
        v = new Variable((BiFunction<String, String, String>) this::simpleBiFunction);
        // maybe we are ok with this, karate "call" can be used only with functions
        assertFalse(v.isJavaFunction());        
    }
    
}
