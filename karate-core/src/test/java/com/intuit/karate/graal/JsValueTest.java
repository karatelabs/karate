package com.intuit.karate.graal;

import java.math.BigInteger;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
public class JsValueTest {

    @Test
    void testTruthy() {
        assertFalse(JsValue.isTruthy(null));
        assertFalse(JsValue.isTruthy(false));
        assertFalse(JsValue.isTruthy(Boolean.FALSE));
        assertTrue(JsValue.isTruthy(true));
        assertTrue(JsValue.isTruthy(Boolean.TRUE));
        assertFalse(JsValue.isTruthy(0));
        assertFalse(JsValue.isTruthy(0.0));
        assertFalse(JsValue.isTruthy(BigInteger.ZERO));
        assertTrue(JsValue.isTruthy(1));
        assertTrue(JsValue.isTruthy(1.0));        
        assertTrue(JsValue.isTruthy(BigInteger.ONE));
        assertTrue(JsValue.isTruthy(""));
        assertTrue(JsValue.isTruthy(Collections.emptyList()));
        assertTrue(JsValue.isTruthy(Collections.emptyMap()));
    }

}
