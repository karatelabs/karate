package com.intuit.karate.graal;

import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class JsAsyncTest {

    static final Logger logger = LoggerFactory.getLogger(JsAsyncTest.class);

    @Test
    void testAsync() throws Exception {
        AtomicReference<String> ref = new AtomicReference();
        JsEngine je = JsEngine.local();
        je.eval("var world = function(){ return 'world' }");
        Value fun = je.evalForValue("(function(){ return 'hello ' + world() })");
        Runnable code = () -> {
            JsEngine child = JsEngine.local();
            child.put("hello", JsAsync.of(fun));
            JsValue res = child.eval("hello()");
            String result = res.getAsString();
            logger.debug("result: {}", result);
            ref.set(result);
        };
        Thread thread = new Thread(code);
        thread.start();
        thread.join();
        assertEquals("hello world", ref.get());
    }

}
