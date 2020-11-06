package com.intuit.karate.graal;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ValueTest {

    static final Logger logger = LoggerFactory.getLogger(ValueTest.class);
    
    @Test
    void testValue() {
        SimplePojo sp = new SimplePojo();
        Value v = Value.asValue(sp);
        assertTrue(v.isHostObject());
    }

}
