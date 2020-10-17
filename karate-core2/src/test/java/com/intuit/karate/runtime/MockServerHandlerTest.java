package com.intuit.karate.runtime;

import com.intuit.karate.core.Feature;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class MockServerHandlerTest {
    
     static final Logger logger = LoggerFactory.getLogger(MockServerHandlerTest.class);
     
     @Test
     void testHandle() {
         Feature feature = RuntimeUtils.toFeature("* print 'foo'");
         MockServerHandler handler = new MockServerHandler(feature);

     }
    
}
