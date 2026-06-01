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
package io.karatelabs.core;

import org.junit.jupiter.api.Test;

import static io.karatelabs.core.TestUtils.*;

class ChannelTest {

    @Test
    void testConfigureUnknownKeyFails() {
        // 'configure' is strict — channels self-configure via their JS object, not global config,
        // so any unrecognized key (incl. an old 'configure kafka = {...}') fails loudly as a typo
        ScenarioRuntime sr = run("""
            * configure kafka = { 'bootstrap.servers': '127.0.0.1:29092' }
            """);
        assertFailed(sr);
        assertFailedWith(sr, "unexpected 'configure' key: 'kafka'");
    }

    @Test
    void testChannelUnknownTypeFails() {
        // resolves by convention to io.karatelabs.ext.unknown.UnknownChannelFactory — not present
        ScenarioRuntime sr = run("""
            * def ch = karate.channel('unknown')
            """);
        assertFailed(sr);
        assertFailedWith(sr, "cannot find [unknown]");
    }

    @Test
    void testChannelMissingDependencyFails() {
        // kafka factory class doesn't exist in test classpath
        ScenarioRuntime sr = run("""
            * def ch = karate.channel('kafka')
            """);
        assertFailed(sr);
        assertFailedWith(sr, "karate-kafka");
    }

}
