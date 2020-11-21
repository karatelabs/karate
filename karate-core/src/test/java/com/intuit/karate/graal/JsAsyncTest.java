package com.intuit.karate.graal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(1);
        final AtomicBoolean hadException = new AtomicBoolean(false);
        String code = "(function(x,y) { return JSON.stringify({x:x,y:y}); })";
        JsEngine je1 = JsEngine.local();
        Value fun1 = je1.evalForValue(code);
        JsAsync async = JsAsync.of(fun1);
        Thread thread = new Thread(() -> {
            try {
                startGate.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < 1000; i++) {
                try {
                    String encoded = (String) async.call(42, 43);
                    assertEquals("{\"x\":42,\"y\":43}", encoded);
                    // logger.debug("ok: {}", i);
                } catch (Exception e) {
                    logger.error("{}", e.getMessage());
                    hadException.set(true);
                }
            }
            endGate.countDown();
        });
        thread.start();
        startGate.countDown();
        for (int i = 0; i < 1000; i++) {
            try {
                String encoded = (String) async.call(42, 43);
                assertEquals("{\"x\":42,\"y\":43}", encoded);
                // logger.debug("ok: {}", i);
            } catch (Exception e) {
                logger.error("{}", e.getMessage());
                hadException.set(true);
            }
        }
        endGate.await();
        thread.join();
        assertFalse(hadException.get());
    }

}
