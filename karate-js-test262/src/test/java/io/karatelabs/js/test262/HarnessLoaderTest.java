package io.karatelabs.js.test262;

import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HarnessLoaderTest {

    @Test
    void testConcurrentPrimeEngineIsSafe(@TempDir Path tempDir) throws Exception {
        // Minimal synthetic harness — just enough content that evalHelper can parse +
        // execute it. The two mandatory helpers come first; the rest are stand-ins for
        // the per-test `includes` field.
        Path harness = Files.createDirectory(tempDir.resolve("harness"));
        Files.writeString(harness.resolve("assert.js"),
                "var assert = { sameValue: function(a, b) { if (a !== b) throw new Error('assert.sameValue'); } };\n");
        Files.writeString(harness.resolve("sta.js"),
                "var $DONOTEVALUATE = function() { throw new Error('do not evaluate'); };\n");
        Files.writeString(harness.resolve("helperA.js"), "var helperA = 'A';\n");
        Files.writeString(harness.resolve("helperB.js"), "var helperB = 'B';\n");

        HarnessLoader loader = new HarnessLoader(tempDir);

        // Fire many parallel primeEngine calls on fresh Engines; each must pick up all
        // four helpers successfully and the shared cache must not corrupt.
        ExecutorService pool = Executors.newFixedThreadPool(8);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < 500; i++) {
                pool.submit(() -> {
                    try {
                        Engine e = new Engine();
                        loader.primeEngine(e, List.of("helperA.js", "helperB.js"));
                        // If the cache serves corrupted source, either eval throws here or
                        // the globals come back wrong.
                        assertEquals("A", e.eval("helperA"));
                        assertEquals("B", e.eval("helperB"));
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }

        if (!errors.isEmpty()) {
            Throwable first = errors.peek();
            fail("concurrent primeEngine had " + errors.size() + " errors; first: " + first, first);
        }
    }
}
