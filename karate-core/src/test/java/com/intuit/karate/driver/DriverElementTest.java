package com.intuit.karate.driver;

import com.intuit.karate.core.Variable;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class DriverElementTest {
    
    static final Logger logger = LoggerFactory.getLogger(DriverElementTest.class);

    @Test
    void testToJson() {       
        Element de = DriverElement.locatorExists(null, "foo");
        List list = Collections.singletonList(de);
        Variable v = new Variable(list);
        logger.debug("element serialized: {}", v.getAsString());
    }

}
