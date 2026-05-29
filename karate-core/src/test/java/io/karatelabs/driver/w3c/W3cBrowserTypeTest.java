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
package io.karatelabs.driver.w3c;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

class W3cBrowserTypeTest {

    /**
     * Regression:
     * {@code W3cDriver} passes the port arg via {@code ProcessBuilder.command().add(...)},
     * which treats the entire string as a single argv element. A format like
     * {@code "--port %d"} therefore becomes one token (e.g. {@code "--port 4444"}) that
     * the driver executable doesn't recognise — it falls back to its default port and
     * Karate times out waiting on the expected port. Every browser's port arg must
     * collapse to a single whitespace-free argv token; {@code "--port=%d"} is the
     * canonical form.
     */
    @ParameterizedTest
    @EnumSource(W3cBrowserType.class)
    void portArgIsSingleArgvToken(W3cBrowserType type) {
        String arg = type.formatPortArg(type.getDefaultPort());
        assertFalse(arg.contains(" "),
                type.getKarateType() + " port arg must not contain whitespace "
                        + "(ProcessBuilder.add() won't split it), got: " + arg);
    }

}
