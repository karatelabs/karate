/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.driver;

import io.karatelabs.gherkin.Feature;
import io.karatelabs.js.Engine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DriverApiTest {

    @Test
    void testAsSupplierWrapsJsArrowFunction() {
        // A JS arrow function, retrieved from karate-js into Java, is a
        // JsFunctionWrapper (which implements JavaCallable). asSupplier()
        // must adapt it to a Supplier that invokes the callable — this is
        // how waitUntil(() => ...) is routed to the Supplier overload and
        // polled locally in karate-js, instead of being stringified and
        // shipped to a browser where its closure scope wouldn't exist.
        Engine engine = new Engine();
        engine.eval("var counter = 0; var fn = () => ++counter >= 3");
        Object fn = engine.get("fn");

        Supplier<Object> supplier = DriverApi.asSupplier(fn, null);
        assertNotNull(supplier, "JS function must be recognised as a callable");

        assertEquals(false, supplier.get());
        assertEquals(false, supplier.get());
        assertEquals(true, supplier.get());

        // The counter lives in karate-js — if the supplier had stringified
        // and shipped the arrow elsewhere, this would be 0 or blow up.
        assertEquals(3, engine.get("counter"));
    }

    @Test
    void testAsSupplierWrapsNamedJsFunction() {
        Engine engine = new Engine();
        engine.eval("function isReady() { return true }");
        Supplier<Object> supplier = DriverApi.asSupplier(engine.get("isReady"), null);
        assertNotNull(supplier);
        assertEquals(true, supplier.get());
    }

    @Test
    void testAsSupplierPassesThroughJavaSupplier() {
        Supplier<Object> original = () -> "hello";
        Supplier<Object> wrapped = DriverApi.asSupplier(original, null);
        assertNotNull(wrapped);
        assertEquals("hello", wrapped.get());
    }

    @Test
    void testAsSupplierWrapsJavaCallable() {
        Callable<Object> callable = () -> 42;
        Supplier<Object> wrapped = DriverApi.asSupplier(callable, null);
        assertNotNull(wrapped);
        assertEquals(42, wrapped.get());
    }

    @Test
    void testAsSupplierReturnsNullForNonCallable() {
        // Strings, numbers, null — all should fall through so the caller
        // can pick the String overload instead.
        assertNull(DriverApi.asSupplier("window.ready === true", null));
        assertNull(DriverApi.asSupplier(42, null));
        assertNull(DriverApi.asSupplier(null, null));
    }

    @Test
    void testWaitCallableFeatureParses() {
        // The full E2E scenarios in wait-callable.feature are exercised via
        // DriverFeatureTest (requires Docker). This check keeps the feature
        // file's syntax under CI coverage without a browser — if someone
        // breaks the Gherkin while editing, the cheap test fails first.
        Feature feature = Feature.read(
                "classpath:io/karatelabs/driver/features/wait-callable.feature");
        assertNotNull(feature);
        assertEquals(4, feature.getSections().size(),
                "wait-callable.feature should have 4 regression scenarios");
    }

}
