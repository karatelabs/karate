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
package io.karatelabs.core.mock;

import io.karatelabs.core.MockServer;
import io.karatelabs.core.ScenarioRuntime;
import io.karatelabs.core.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mock binds every interface unless given a host; {@code localhost} binds loopback only.
 */
public class MockServerHostTest {

    static final String MOCK = """
            Feature: mock

            Scenario: pathMatches('/ping')
              * def response = { ok: true }
            """;

    /** Called from a scenario: the interface the mock that karate.start() returned is bound to. */
    public static boolean loopback(Object server) {
        InetSocketAddress address = ((MockServer) server).getLocalAddress();
        return address.getAddress().isLoopbackAddress();
    }

    @Test
    void builderHostLocalhostBindsLoopback() {
        MockServer server = MockServer.featureString(MOCK).host("localhost").start();
        try {
            InetSocketAddress address = server.getLocalAddress();
            assertTrue(address.getAddress().isLoopbackAddress(), address.toString());
            assertFalse(address.getAddress().isAnyLocalAddress(), address.toString());
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void builderWithoutHostBindsAllInterfaces() {
        MockServer server = MockServer.featureString(MOCK).start();
        try {
            InetSocketAddress address = server.getLocalAddress();
            assertTrue(address.getAddress().isAnyLocalAddress(), address.toString());
            assertEquals("http://localhost:" + server.getPort(), server.getUrl());
        } finally {
            server.stopAndWait();
        }
    }

    @Test
    void karateStartHostBindsLoopback(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("api.feature"), MOCK);
        ScenarioRuntime sr = TestUtils.runFromDir(dir, """
                * def server = karate.start({ mock: 'api.feature', port: 0, host: 'localhost' })
                * def loopback = Java.type('io.karatelabs.core.mock.MockServerHostTest').loopback(server)
                * match loopback == true
                """);
        assertTrue(sr.getResult().isPassed(), () -> sr.getResult().getFailureMessageWithLocation());
    }

    @Test
    void karateStartWithoutHostBindsAllInterfaces(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("api.feature"), MOCK);
        ScenarioRuntime sr = TestUtils.runFromDir(dir, """
                * def server = karate.start({ mock: 'api.feature', port: 0 })
                * def loopback = Java.type('io.karatelabs.core.mock.MockServerHostTest').loopback(server)
                * match loopback == false
                """);
        assertTrue(sr.getResult().isPassed(), () -> sr.getResult().getFailureMessageWithLocation());
    }

}
