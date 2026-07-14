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
package io.karatelabs.junit6;

import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the failure a JUnit / surefire run actually surfaces: the {@link DynamicTest} for a
 * failed scenario must throw a Throwable whose message carries the offending
 * {@code path.feature:line} (on its own, IDE-hyperlinkable line) while preserving the original
 * stack frames. Without this, a raw {@code SocketTimeoutException} (or any step failure) shows
 * only engine / HTTP-client internals and the reader can't tell which step failed.
 */
class ErrorDecorationTest {

    @Test
    void testFailingScenarioThrowsErrorWithFeatureLocation() {
        List<DynamicNode> nodes = Karate.run("failing/boom")
                .relativeTo(getClass())
                .hierarchical(false)
                .stream()
                .collect(Collectors.toList());

        DynamicTest failing = nodes.stream()
                .filter(n -> n instanceof DynamicTest)
                .map(n -> (DynamicTest) n)
                .filter(t -> t.getDisplayName().contains("bad match"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("failing dynamic test not found in: " + nodes));

        Throwable thrown = assertThrows(Throwable.class, () -> failing.getExecutable().execute());

        String message = thrown.getMessage();
        assertNotNull(message);
        assertTrue(message.startsWith("match failed"),
                "original error must lead the message: " + message);
        String lastLine = message.substring(message.lastIndexOf('\n') + 1);
        assertTrue(lastLine.endsWith("boom.feature:5"),
                "thrown error must end with the feature location line, got: " + lastLine);
        // IntelliJ console filter hyperlink contract (KarateConsoleFilter.FEATURE_LINE_PATTERN)
        assertTrue(lastLine.matches("\\s*\\S.*\\.feature:\\d+"),
                "location line must satisfy the IDE hyperlink pattern: '" + lastLine + "'");

        // synthetic <feature> frame on top, real frames preserved below
        StackTraceElement[] trace = thrown.getStackTrace();
        assertEquals("<feature>", trace[0].getClassName());
        assertTrue(trace.length > 1, "original stack frames must be preserved beneath <feature>");
    }

}
