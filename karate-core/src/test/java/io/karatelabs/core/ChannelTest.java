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

import java.util.Map;

import static io.karatelabs.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class ChannelTest {

    @Test
    void testConfigureKafkaStoresOptions() {
        ScenarioRuntime sr = run("""
            * configure kafka = { 'bootstrap.servers': '127.0.0.1:29092' }
            * def cfg = karate.config
            """);
        assertPassed(sr);
        KarateConfig config = sr.getConfig();
        Map<String, Object> options = config.getChannelOptions("kafka");
        assertNotNull(options);
        assertEquals("127.0.0.1:29092", options.get("bootstrap.servers"));
    }

    @Test
    void testConfigureGrpcStoresOptions() {
        ScenarioRuntime sr = run("""
            * configure grpc = { host: 'localhost', port: 50051 }
            """);
        assertPassed(sr);
        KarateConfig config = sr.getConfig();
        Map<String, Object> options = config.getChannelOptions("grpc");
        assertNotNull(options);
        assertEquals("localhost", options.get("host"));
    }

    @Test
    void testConfigureUnknownKeyFails() {
        ScenarioRuntime sr = run("""
            * configure foobar = { x: 1 }
            """);
        assertFailed(sr);
        assertFailedWith(sr, "unexpected 'configure' key: 'foobar'");
    }

    @Test
    void testChannelUnknownTypeFails() {
        ScenarioRuntime sr = run("""
            * def ch = karate.channel('unknown')
            """);
        assertFailed(sr);
        assertFailedWith(sr, "unknown channel type: unknown");
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

    @Test
    void testChannelTypeRegistration() {
        assertTrue(KarateConfig.isChannelType("kafka"));
        assertTrue(KarateConfig.isChannelType("grpc"));
        assertTrue(KarateConfig.isChannelType("websocket"));
        assertFalse(KarateConfig.isChannelType("ftp"));
        assertFalse(KarateConfig.isChannelType(""));
    }

    @Test
    void testChannelFactoryClassMapping() {
        assertEquals("io.karatelabs.kafka.KafkaChannelFactory", KarateConfig.getChannelFactoryClass("kafka"));
        assertEquals("io.karatelabs.grpc.GrpcChannelFactory", KarateConfig.getChannelFactoryClass("grpc"));
        assertNull(KarateConfig.getChannelFactoryClass("nonexistent"));
    }

    @Test
    void testConfigureChannelNullRemoves() {
        ScenarioRuntime sr = run("""
            * configure kafka = { 'bootstrap.servers': '127.0.0.1:29092' }
            * configure kafka = null
            """);
        assertPassed(sr);
        assertNull(sr.getConfig().getChannelOptions("kafka"));
    }

}
