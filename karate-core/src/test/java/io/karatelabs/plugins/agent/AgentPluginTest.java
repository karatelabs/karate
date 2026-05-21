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
package io.karatelabs.plugins.agent;

import io.karatelabs.core.Runner;
import io.karatelabs.core.Suite;
import io.karatelabs.core.SuiteRunEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AgentPlugin} — wire shape, property setters, and the
 * lazy-activation lifecycle, without actually sending HTTP. Mirrors the contract
 * the retired {@code HttpPostListenerTest} pinned down for the env-var-driven
 * predecessor; rewritten against the {@code boot.plugin('agent')} activation path.
 */
class AgentPluginTest {

    @Test
    void inert_whenUrlNotSet() {
        AgentPlugin plugin = new AgentPlugin();
        // No url → onEvent must be a complete no-op: no activation, no buffering.
        Suite suite = Runner.builder().buildSuite();
        plugin.onEvent(SuiteRunEvent.enter(suite));
        assertFalse(plugin.isActivatedForTest(), "should not activate without url");
        assertEquals(0, plugin.bufferSizeForTest());
        assertNull(plugin.getRunId());
    }

    @Test
    void activates_lazilyOnFirstEvent_whenUrlSet() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");

        Suite suite = Runner.builder().buildSuite();
        plugin.onEvent(SuiteRunEvent.enter(suite));

        assertTrue(plugin.isActivatedForTest(), "first event with url set should activate");
        assertNotNull(plugin.getRunId(), "runId minted on activation");
        assertTrue(plugin.getRunId().matches("[0-9a-f-]{36}"),
                "runId not UUID-shaped: " + plugin.getRunId());
        assertEquals(1, plugin.bufferSizeForTest(), "SUITE_ENTER should buffer");
    }

    @Test
    void serialize_includesSchemaVersionAndDialect_onEveryEvent() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter); // trigger activation so runId exists for the assertion below
        String line = plugin.serialize(enter);

        assertTrue(line.contains("\"schema\":{\"version\":1,\"dialect\":\"karate-v2\"}"),
                "envelope missing schema fields: " + line);
        assertTrue(line.contains("\"type\":\"SUITE_ENTER\""),
                "envelope missing type: " + line);
    }

    @Test
    void serialize_addsRunIdAndKarateVersion_onSuiteEnter() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.onBoot(buildSuiteWithEnv("qa"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter);
        String line = plugin.serialize(enter);

        assertTrue(line.contains("\"runId\":\"" + plugin.getRunId() + "\""),
                "SUITE_ENTER missing runId: " + line);
        assertTrue(line.contains("\"karateVersion\":"),
                "SUITE_ENTER missing karateVersion: " + line);
        assertTrue(line.contains("\"env\":\"qa\""),
                "SUITE_ENTER missing env from onBoot: " + line);
    }

    @Test
    void getMode_defaultsToBatch() {
        AgentPlugin plugin = new AgentPlugin();
        assertEquals("batch", plugin.getMode());
    }

    @Test
    void setMode_acceptsBatchAndFinal_caseInsensitive() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setMode("final");
        assertEquals("final", plugin.getMode());
        plugin.setMode("BATCH");
        assertEquals("batch", plugin.getMode());
    }

    @Test
    void setMode_failsFast_onInvalidValue() {
        AgentPlugin plugin = new AgentPlugin();
        // Eager validation per K43 — bad mode throws at the assignment line in karate-boot.js.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> plugin.setMode("streaming"));
        assertTrue(ex.getMessage().contains("agent.mode"),
                "message should name the property: " + ex.getMessage());
    }

    @Test
    void setUrl_stripsTrailingSlash() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444/");
        assertEquals("http://localhost:4444", plugin.getUrl());
    }

    @Test
    void setUrl_nullOrBlank_leavesPluginInert() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl(null);
        assertNull(plugin.getUrl());
        plugin.setUrl("   ");
        assertNull(plugin.getUrl());
    }

    @Test
    void twoListeners_haveDistinctRunIds() {
        AgentPlugin a = newActivatedPlugin();
        AgentPlugin b = newActivatedPlugin();
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

        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.setParams(params);
        plugin.onBoot(buildSuiteWithEnv("qa"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter);
        String line = plugin.serialize(enter);

        assertTrue(line.contains("\"team\":\"payments\""), "team key missing: " + line);
        assertTrue(line.contains("\"branch\":\"feature/abc\""), "branch key missing: " + line);
        assertTrue(line.contains("\"dev\":false"), "dev key missing: " + line);
    }

    @Test
    void setProject_attachesSlugToSuiteEnter() {
        // The receiver (karate-agent dashboard) reads data.project to auto-create
        // / resolve the project and bind the run to it via SUITE_ENTER.
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.setProject("acme-billing");
        plugin.onBoot(buildSuiteWithEnv("ci"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter);
        String line = plugin.serialize(enter);

        assertTrue(line.contains("\"project\":\"acme-billing\""),
                "SUITE_ENTER should carry project slug: " + line);
    }

    @Test
    void serialize_omitsProject_whenProjectUnset() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter);
        String line = plugin.serialize(enter);

        assertFalse(line.contains("\"project\""),
                "SUITE_ENTER should not carry project when unset: " + line);
    }

    @Test
    void setProject_blankOrNullClearsTheBinding() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setProject("anything");
        plugin.setProject("");
        assertNull(plugin.getProject(), "blank slug clears project binding");
        plugin.setProject("anything");
        plugin.setProject(null);
        assertNull(plugin.getProject(), "null slug clears project binding");
    }

    @Test
    void setProject_trimsWhitespace() {
        // The receiver-side Project.SLUG_PATTERN doesn't allow whitespace, so
        // trim client-side to keep the wire field canonical.
        AgentPlugin plugin = new AgentPlugin();
        plugin.setProject("  acme-billing  ");
        assertEquals("acme-billing", plugin.getProject(), "leading + trailing whitespace stripped");
        plugin.setProject("\tacme\n");
        assertEquals("acme", plugin.getProject(), "tab/newline stripped");
        plugin.setProject("   ");
        assertNull(plugin.getProject(), "whitespace-only treated as blank → cleared");
    }

    @Test
    void serialize_omitsParams_whenParamsNull() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.onBoot(buildSuiteWithEnv("dev"));

        Suite suite = Runner.builder().buildSuite();
        SuiteRunEvent enter = SuiteRunEvent.enter(suite);
        plugin.onEvent(enter);
        String line = plugin.serialize(enter);

        assertFalse(line.contains("\"params\""),
                "SUITE_ENTER should not carry params when none set: " + line);
    }

    @Test
    void manifest_carriesVersionAndConfigSummary() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        plugin.setMode("final");

        Map<String, Object> manifest = plugin.getManifest();
        assertNotNull(manifest.get("version"), "manifest must carry the karate version");
        assertEquals("http://localhost:4444", manifest.get("url"));
        assertEquals("final", manifest.get("mode"));
    }

    @Test
    void manifest_omitsUrl_whenNotConfigured() {
        // Plugin booted but never given a url → manifest carries mode + version only.
        // (Token is intentionally never in the manifest — it's sensitive.)
        AgentPlugin plugin = new AgentPlugin();
        Map<String, Object> manifest = plugin.getManifest();
        assertFalse(manifest.containsKey("url"));
        assertEquals("batch", manifest.get("mode"));
    }

    private AgentPlugin newActivatedPlugin() {
        AgentPlugin plugin = new AgentPlugin();
        plugin.setUrl("http://localhost:4444");
        Suite suite = Runner.builder().buildSuite();
        plugin.onEvent(SuiteRunEvent.enter(suite));
        return plugin;
    }

    private static Suite buildSuiteWithEnv(String env) {
        return Runner.builder().karateEnv(env).buildSuite();
    }
}
