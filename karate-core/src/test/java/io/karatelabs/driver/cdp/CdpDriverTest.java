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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CdpDriverTest {

    @Test
    void testTransientContextErrorRetries() {
        CdpResponse response = new CdpResponse(Map.of(
                "id", 1,
                "error", Map.of("code", -32000, "message", "Cannot find context with specified id")
        ));
        assertTrue(CdpDriver.isTransientContextError(response));
    }

    @Test
    void testObjectReferenceChainNotTransient() {
        CdpResponse response = new CdpResponse(Map.of(
                "id", 2,
                "error", Map.of("code", -32000, "message", "Object reference chain is too long")
        ));
        assertFalse(CdpDriver.isTransientContextError(response));
    }

    @Test
    void testSuccessResponseNotTransient() {
        CdpResponse response = new CdpResponse(Map.of(
                "id", 3,
                "result", Map.of("value", "ok")
        ));
        assertFalse(CdpDriver.isTransientContextError(response));
    }

    @Test
    void testNonContextErrorNotTransient() {
        CdpResponse response = new CdpResponse(Map.of(
                "id", 4,
                "error", Map.of("code", -32600, "message", "Invalid request")
        ));
        assertFalse(CdpDriver.isTransientContextError(response));
    }

}
