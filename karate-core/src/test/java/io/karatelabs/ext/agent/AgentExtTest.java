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
package io.karatelabs.ext.agent;

import io.karatelabs.core.Runner;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteRunEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgentExt} — wire shape, property setters, and the
 * lazy-activation lifecycle, without actually sending HTTP. Mirrors the contract
 * the retired {@code HttpPostListenerTest} pinned down for the env-var-driven
 * predecessor; rewritten against the {@code boot.ext('agent')} activation path.
 */
class AgentExtTest {

    @Test
    void inert_whenUrlNotSet() {
        AgentExt ext = new AgentExt();
        // No url → onEvent must be a complete no-op: no activation, no buffering.
        Suite suite = Runner.builder().buildSuite();
        ext.onEvent(SuiteRunEvent.enter(suite));
        assertFalse(ext.isActivatedForTest(), "should not activate without url");
        assertEquals(0, ext.bufferSizeForTest());
        assertNull(ext.getRunId());
    }

    @Test
    void activates_lazilyOnFirstEvent_whenUrlSet() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");

        Suite suite = Runner.builder().buildSuite();
        ext.onEvent(SuiteRunEvent.enter(suite));

        assertTrue(ext.isActivatedForTest(), "first event with url set should activate");
        assertNotNull(ext.getRunId(), "runId minted on activation");
        assertTrue(ext.getRunId().matches("[0-9a-f-]{36}"),
                "runId not UUID-shaped: " + ext.getRunId());
        assertEquals(1, ext.bufferSizeForTest(), "SUITE_ENTER should buffer");
    }

    @Test
    void serialize_includesSchemaVersionAndDialect_onEveryEvent() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter); // trigger activation so runId exists for the assertion below
        String line = ext.serialize(enter);

        assertTrue(line.contains("\"schema\":{\"version\":1,\"dialect\":\"karate-v2\"}"),
                "envelope missing schema fields: " + line);
        assertTrue(line.contains("\"type\":\"SUITE_ENTER\""),
                "envelope missing type: " + line);
    }

    @Test
    void serialize_addsRunIdAndKarateVersion_onSuiteEnter() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.onBoot(buildSuiteWithEnv("qa"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter);
        String line = ext.serialize(enter);

        assertTrue(line.contains("\"runId\":\"" + ext.getRunId() + "\""),
                "SUITE_ENTER missing runId: " + line);
        assertTrue(line.contains("\"karateVersion\":"),
                "SUITE_ENTER missing karateVersion: " + line);
        assertTrue(line.contains("\"env\":\"qa\""),
                "SUITE_ENTER missing env from onBoot: " + line);
    }

    @Test
    void getMode_defaultsToBatch() {
        AgentExt ext = new AgentExt();
        assertEquals("batch", ext.getMode());
    }

    @Test
    void setMode_acceptsBatchAndFinal_caseInsensitive() {
        AgentExt ext = new AgentExt();
        ext.setMode("final");
        assertEquals("final", ext.getMode());
        ext.setMode("BATCH");
        assertEquals("batch", ext.getMode());
    }

    @Test
    void setMode_failsFast_onInvalidValue() {
        AgentExt ext = new AgentExt();
        // Eager validation per K43 — bad mode throws at the assignment line in karate-boot.js.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ext.setMode("streaming"));
        assertTrue(ex.getMessage().contains("agent.mode"),
                "message should name the property: " + ex.getMessage());
    }

    @Test
    void setUrl_stripsTrailingSlash() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444/");
        assertEquals("http://localhost:4444", ext.getUrl());
    }

    @Test
    void setUrl_nullOrBlank_leavesExtInert() {
        AgentExt ext = new AgentExt();
        ext.setUrl(null);
        assertNull(ext.getUrl());
        ext.setUrl("   ");
        assertNull(ext.getUrl());
    }

    @Test
    void twoListeners_haveDistinctRunIds() {
        AgentExt a = newActivatedExt();
        AgentExt b = newActivatedExt();
        assertNotEquals(a.getRunId(), b.getRunId());
    }

    @Test
    void setParams_acceptsArbitraryMap_forwardCompatible() {
        // Unknown keys (anything beyond V0's `dev` flag) must survive the wire so
        // future receivers can read them without a karate-core release.
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dev", false);
        params.put("team", "payments");
        params.put("branch", "feature/abc");

        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.setParams(params);
        ext.onBoot(buildSuiteWithEnv("qa"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter);
        String line = ext.serialize(enter);

        assertTrue(line.contains("\"team\":\"payments\""), "team key missing: " + line);
        assertTrue(line.contains("\"branch\":\"feature/abc\""), "branch key missing: " + line);
        assertTrue(line.contains("\"dev\":false"), "dev key missing: " + line);
    }

    @Test
    void setProject_attachesSlugToSuiteEnter() {
        // The receiver (karate-agent dashboard) reads data.project to auto-create
        // / resolve the project and bind the run to it via SUITE_ENTER.
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.setProject("acme-billing");
        ext.onBoot(buildSuiteWithEnv("ci"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter);
        String line = ext.serialize(enter);

        assertTrue(line.contains("\"project\":\"acme-billing\""),
                "SUITE_ENTER should carry project slug: " + line);
    }

    @Test
    void serialize_omitsProject_whenProjectUnset() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter);
        String line = ext.serialize(enter);

        assertFalse(line.contains("\"project\""),
                "SUITE_ENTER should not carry project when unset: " + line);
    }

    @Test
    void setProject_blankOrNullClearsTheBinding() {
        AgentExt ext = new AgentExt();
        ext.setProject("anything");
        ext.setProject("");
        assertNull(ext.getProject(), "blank slug clears project binding");
        ext.setProject("anything");
        ext.setProject(null);
        assertNull(ext.getProject(), "null slug clears project binding");
    }

    @Test
    void setProject_trimsWhitespace() {
        // The receiver-side Project.SLUG_PATTERN doesn't allow whitespace, so
        // trim client-side to keep the wire field canonical.
        AgentExt ext = new AgentExt();
        ext.setProject("  acme-billing  ");
        assertEquals("acme-billing", ext.getProject(), "leading + trailing whitespace stripped");
        ext.setProject("\tacme\n");
        assertEquals("acme", ext.getProject(), "tab/newline stripped");
        ext.setProject("   ");
        assertNull(ext.getProject(), "whitespace-only treated as blank → cleared");
    }

    @Test
    void serialize_omitsParams_whenParamsNull() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        ext.onEvent(enter);
        String line = ext.serialize(enter);

        assertFalse(line.contains("\"params\""),
                "SUITE_ENTER should not carry params when none set: " + line);
    }

    @Test
    void manifest_carriesVersionAndConfigSummary() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        ext.setMode("final");

        Map<String, Object> manifest = ext.getManifest();
        assertNotNull(manifest.get("version"), "manifest must carry the karate version");
        assertEquals("http://localhost:4444", manifest.get("url"));
        assertEquals("final", manifest.get("mode"));
    }

    @Test
    void manifest_omitsUrl_whenNotConfigured() {
        // Ext booted but never given a url → manifest carries mode + version only.
        // (Token is intentionally never in the manifest — it's sensitive.)
        AgentExt ext = new AgentExt();
        Map<String, Object> manifest = ext.getManifest();
        assertFalse(manifest.containsKey("url"));
        assertEquals("batch", manifest.get("mode"));
    }

    private AgentExt newActivatedExt() {
        AgentExt ext = new AgentExt();
        ext.setUrl("http://localhost:4444");
        Suite suite = Runner.builder().buildSuite();
        ext.onEvent(SuiteRunEvent.enter(suite));
        return ext;
    }

    private static Suite buildSuiteWithEnv(String env) {
        return Runner.builder().karateEnv(env).buildSuite();
    }
}
