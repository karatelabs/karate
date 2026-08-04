/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.profiling;

import io.karatelabs.core.MockServer;

/**
 * Main class of the <em>sibling</em> mock-server JVM.
 *
 * <p>The mock is a separate process on purpose. In-process, its CPU samples and
 * allocations land in the same recording as the load driver's, and every number in the
 * digest becomes a blend of client and server. The cost is a localhost socket hop, which
 * is closer to what a real run does anyway.
 *
 * <p>Lifecycle is tied to the parent's stdin pipe: when the parent dies — including an
 * ungraceful kill, where a shutdown hook would not run — this process reads EOF and
 * exits. Without that, a Ctrl-C'd run leaves a mock holding its port and poisons the next
 * one.
 */
public final class MockJvm {

    /** The parent blocks on this line to learn the port; keep the format stable. */
    static final String READY_PREFIX = "PROFILING-MOCK-URL ";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: MockJvm <feature-path>");
        }
        MockServer server = MockServer.feature(args[0]).start();
        System.out.println(READY_PREFIX + server.getUrl());
        System.out.flush();

        // Block until the parent's pipe closes. read() returns -1 on EOF; it also returns
        // if the parent deliberately writes a byte to ask for shutdown.
        try {
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (Exception ignored) {
            // treat any stdin failure as "parent is gone"
        }
        server.stopAndWait();
    }

}
