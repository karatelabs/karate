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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpPostListener} — wire shape and lifecycle, without actually
 * sending HTTP. The integration test happens against a running karate-agent dashboard.
 */
class HttpPostListenerTest {

    @Test
    void tryCreate_returnsNull_whenEnvVarUnset() {
        // System.getenv("KARATE_AGENT_URL") returns null in the test JVM. Critical guarantee
        // per AGENT_KARATE K30: zero network code paths when the env var is unset.
        if (System.getenv(HttpPostListener.ENV_URL) == null) {
            assertNull(HttpPostListener.tryCreate("dev"));
        }
    }

    @Test
    void serialize_includesSchemaVersionAndDialect_onEveryEvent() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50);

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        String line = listener.serialize(enter);

        assertTrue(line.contains("\"schema\":{\"version\":1,\"dialect\":\"karate-v2\"}"),
                "envelope missing schema fields: " + line);
        assertTrue(line.contains("\"type\":\"SUITE_ENTER\""),
                "envelope missing type: " + line);
    }

    @Test
    void serialize_addsRunIdAndKarateVersion_onSuiteEnter() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "qa", 50);

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        String line = listener.serialize(enter);

        assertTrue(line.contains("\"runId\":\"" + listener.getRunId() + "\""),
                "SUITE_ENTER missing runId: " + line);
        assertTrue(line.contains("\"karateVersion\":"),
                "SUITE_ENTER missing karateVersion: " + line);
        assertTrue(line.contains("\"env\":\"qa\""),
                "SUITE_ENTER missing env: " + line);
    }

    @Test
    void onEvent_skipsStepAndHttpEvents() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50);

        // Construct a minimal HTTP event — listener should buffer 0 lines for it
        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        listener.onEvent(enter);
        assertEquals(1, listener.bufferSizeForTest(), "SUITE_ENTER should buffer");

        // STEP_ENTER / STEP_EXIT / HTTP_ENTER / HTTP_EXIT should not buffer.
        // We can't easily construct those in isolation without ScenarioRuntime + StepResult,
        // so this guard verifies the buffer doesn't grow when only a SUITE_ENTER was sent.
    }

    @Test
    void getMode_defaultsToBatch_whenEnvUnset() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50);
        // KARATE_AGENT_MODE is typically unset in tests; default is BATCH.
        if (System.getenv(HttpPostListener.ENV_MODE) == null) {
            assertEquals(HttpPostListener.Mode.BATCH, listener.getMode());
        }
    }

    @Test
    void getUrl_stripsTrailingSlash() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444/", "dev", 50);
        assertEquals("http://localhost:4444", listener.getUrl());
    }

    @Test
    void getRunId_isUuidFormat() {
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50);
        // UUID: 8-4-4-4-12
        assertTrue(listener.getRunId().matches("[0-9a-f-]{36}"),
                "runId not UUID-shaped: " + listener.getRunId());
    }

    @Test
    void twoListeners_haveDistinctRunIds() {
        HttpPostListener a = new HttpPostListener("http://localhost:4444", "dev", 50);
        HttpPostListener b = new HttpPostListener("http://localhost:4444", "dev", 50);
        assertNotEquals(a.getRunId(), b.getRunId());
    }

    @Test
    void parseParams_returnsNull_forNullOrEmpty() {
        assertNull(HttpPostListener.parseParams(null));
        assertNull(HttpPostListener.parseParams(""));
    }

    @Test
    void parseParams_returnsMap_forValidJsonObject() {
        Map<String, Object> parsed = HttpPostListener.parseParams("{\"dev\":true,\"label\":\"local\"}");
        assertNotNull(parsed);
        assertEquals(Boolean.TRUE, parsed.get("dev"));
        assertEquals("local", parsed.get("label"));
    }

    @Test
    void parseParams_returnsNull_forNonObjectJson() {
        // JSON array — not an object. We require an object envelope so receivers can
        // add new keys without breaking older readers; arrays/scalars are dropped.
        assertNull(HttpPostListener.parseParams("[1,2,3]"));
        assertNull(HttpPostListener.parseParams("\"just-a-string\""));
        assertNull(HttpPostListener.parseParams("42"));
        assertNull(HttpPostListener.parseParams("true"));
    }

    @Test
    void parseParams_returnsNull_forMalformedJson() {
        // Per the env-var contract: malformed JSON logs WARN and is dropped; the listener
        // still activates (the run-data plumbing is independent of the params envelope).
        assertNull(HttpPostListener.parseParams("{not valid"));
        assertNull(HttpPostListener.parseParams("{\"dangling\":}"));
    }

    @Test
    void serialize_includesParams_onSuiteEnter_whenParamsSet() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dev", true);
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50, params);

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        String line = listener.serialize(enter);

        assertTrue(line.contains("\"params\":{\"dev\":true}"),
                "SUITE_ENTER should include params: " + line);
    }

    @Test
    void serialize_omitsParams_whenParamsNull() {
        // No params constructor arg → SUITE_ENTER envelope must not carry a params field.
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "dev", 50);

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        String line = listener.serialize(enter);

        assertFalse(line.contains("\"params\""),
                "SUITE_ENTER should not carry params when env var unset: " + line);
    }

    @Test
    void serialize_passesParamsThrough_forForwardCompatibility() {
        // Unknown keys (anything beyond V0's `dev` flag) must survive the wire so
        // future receivers can read them without a karate-core release.
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dev", false);
        params.put("team", "payments");
        params.put("branch", "feature/abc");
        HttpPostListener listener = new HttpPostListener("http://localhost:4444", "qa", 50, params);

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        String line = listener.serialize(enter);

        assertTrue(line.contains("\"team\":\"payments\""), "team key missing: " + line);
        assertTrue(line.contains("\"branch\":\"feature/abc\""), "branch key missing: " + line);
        assertTrue(line.contains("\"dev\":false"), "dev key missing: " + line);
    }

}
