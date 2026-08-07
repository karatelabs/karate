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
package io.karatelabs.http;

import io.karatelabs.core.KarateConfig;
import io.karatelabs.core.MockServer;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code apply()} must rebuild the client when the config that shapes it changed, and must not
 * when it did not.
 *
 * <p><b>Why this is a class of its own.</b> Config reaches a client from four places, and only one
 * of them names a key: a {@code configure <key>} step, a shared-scope call returning, a callee
 * inheriting from its caller, and a cached {@code callonce} replaying. The last three copy a whole
 * config across a scenario boundary, so a caller-side "does this key matter" test cannot answer
 * for them and they rebuilt unconditionally — a scenario doing request, shared-scope call, request
 * destroyed a live client, its connection manager and its sockets having changed nothing.
 * {@link HttpClient#apply} now owns that decision, and these are the tests of it.
 *
 * <p><b>Every case here is a pair.</b> A test that only asserts "unchanged config keeps the
 * client" passes just as well against an {@code apply()} that never rebuilds at all, which would
 * be a far worse bug than the one being fixed — a scenario silently running against a client that
 * no longer matches its own config. So each case carries the negative control that fails if the
 * guard is too eager.
 *
 * <p>The probe is the private {@code httpClient} field. Identity is what matters: the same
 * instance means nothing was torn down, and null means it was, because {@code closeQuietly()}
 * nulls the field and {@code invoke()} lazily rebuilds.
 */
class ApacheHttpClientConfigRebuildTest {

    /** The built client, or null if there is none right now. */
    private static Object built(ApacheHttpClient client) throws Exception {
        Field field = ApacheHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return field.get(client);
    }

    /** Build a real client by making one request, and hand back the instance that resulted. */
    private static Object build(ApacheHttpClient client, String url) throws Exception {
        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod("GET");
        client.invoke(request);
        Object instance = built(client);
        assertNotNull(instance, "a request should leave a live client behind");
        return instance;
    }

    private static void withServer(Probe probe) throws Exception {
        MockServer server = MockServer.featureString("""
                Feature: echo

                Scenario: pathMatches('/ping')
                  * def response = { ok: true }
                """).port(0).start();
        try {
            probe.run("http://localhost:" + server.getPort() + "/ping");
        } finally {
            server.stopAndWait();
        }
    }

    private interface Probe {
        void run(String url) throws Exception;
    }

    /**
     * The headline: re-applying an unchanged config keeps the built client, and changing something
     * that reaches the wire drops it.
     */
    @Test
    void testReapplyingAnUnchangedConfigKeepsTheBuiltClient() throws Exception {
        withServer(url -> {
            ApacheHttpClient client = new ApacheHttpClient();
            KarateConfig config = new KarateConfig();
            client.apply(config);
            Object first = build(client, url);

            // The three bulk paths do exactly this: copy a config across a boundary and re-apply
            // it. Nothing changed, so nothing may be torn down.
            client.apply(config);
            assertSame(first, built(client),
                    "re-applying an unchanged config must not tear down the built client");
            client.apply(new KarateConfig());
            assertSame(first, built(client),
                    "a different but equivalent config instance must not tear it down either");

            // Negative control. Without this the test would also pass against an apply() that
            // never rebuilds, which is the worse bug.
            config.configure("readTimeout", 12345);
            client.apply(config);
            assertNull(built(client), "a changed readTimeout must drop the client");
        });
    }

    /**
     * {@code configure retry} sets the retry count the client builder is given, but its key was
     * classified as not needing a rebuild — so the new count reached the config and never reached
     * the client. The guard reads the settings rather than the key, which fixes it.
     */
    @Test
    void testChangingTheRetryCountRebuildsTheClient() throws Exception {
        withServer(url -> {
            ApacheHttpClient client = new ApacheHttpClient();
            KarateConfig config = new KarateConfig();
            config.configure("httpRetryEnabled", true);
            client.apply(config);
            Object first = build(client, url);

            config.configure("retry", Map.of("count", 7));
            client.apply(config);
            assertNull(built(client),
                    "the retry count is baked into the client at build time, so changing it "
                            + "must rebuild — its configure key reports no rebuild needed");

            // Negative control: re-applying the same retry settings changes nothing.
            Object second = build(client, url);
            config.configure("retry", Map.of("count", 7));
            client.apply(config);
            assertSame(second, built(client), "re-setting the same retry count must not rebuild");
        });
    }

    /**
     * The same fix, through the path a feature actually takes.
     *
     * <p><b>This test exists because the one above is not enough on its own, and reviewing the
     * two together is the point.</b> That test calls {@code apply()} by hand, so it proves the key
     * notices a retry change — and nothing more. The step a user writes goes through
     * {@code ScenarioRuntime.configure}, which decides whether to call {@code apply()} at all from
     * the verdict {@code KarateConfig.configure} returns, and that verdict is {@code false} for
     * {@code retry}. A guard inside {@code apply()} cannot fix a bug whose symptom is that
     * {@code apply()} is never reached, so the unit test passed while the user-facing behaviour
     * stayed broken.
     *
     * <p>The probe is what {@code apply()} was actually handed: if the runtime never calls it
     * after the retry change, no invocation ever sees the new count.
     */
    @Test
    void testConfiguringRetryInAFeatureReachesTheClient(@TempDir Path dir) throws Exception {
        List<Integer> retryCountsSeenByApply = Collections.synchronizedList(new ArrayList<>());
        class RecordingClient extends ApacheHttpClient {
            @Override
            public void apply(KarateConfig config) {
                retryCountsSeenByApply.add(config.getRetryCount());
                super.apply(config);
            }
        }
        Files.writeString(dir.resolve("retry.feature"), """
                Feature: a retry change between requests

                  Scenario: reconfigure retry after the client is built
                    * configure httpRetryEnabled = true
                    * configure retry = { count: 7, interval: 0 }
                """);
        SuiteResult result = Runner.builder()
                .path(dir.toAbsolutePath().toString())
                .httpClientFactory(RecordingClient::new)
                .backupOutputDir(false)
                .outputHtmlReport(false)
                .parallel(1);

        assertEquals(0, result.getScenarioFailedCount(), "the feature itself must pass");
        assertTrue(retryCountsSeenByApply.contains(7),
                "configure retry changes the retry count baked into the client at build time, so "
                        + "the runtime must hand the new config to apply() and let it decide — "
                        + "apply() only ever saw " + retryCountsSeenByApply);
    }

    /**
     * {@code getNonProxyHosts()} returns the live list inside the proxy map rather than a copy, so
     * a key that retained the reference would compare it against itself after an in-place edit and
     * conclude nothing had changed. The key copies it.
     */
    @Test
    void testEditingNonProxyHostsInPlaceRebuildsTheClient() throws Exception {
        withServer(url -> {
            ApacheHttpClient client = new ApacheHttpClient();
            KarateConfig config = new KarateConfig();
            Map<String, Object> proxy = new LinkedHashMap<>();
            proxy.put("uri", "http://localhost:9999");
            proxy.put("nonProxyHosts", new ArrayList<>(List.of("localhost")));
            config.configure("proxy", proxy);
            client.apply(config);
            Object first = build(client, url);
            assertSame(first, built(client), "sanity: the client survives its own first apply");

            // Mutate through the live reference, exactly as the getter exposes it.
            List<String> live = config.getNonProxyHosts();
            assertNotNull(live, "the proxy config should expose its nonProxyHosts");
            live.add("example.com");

            client.apply(config);
            assertNull(built(client),
                    "an in-place edit of nonProxyHosts changes what the client routes, so it "
                            + "must rebuild even though the List reference is unchanged");
        });
    }

    /**
     * Auth settings other than NTLM are not read by {@code apply()}, so switching between them
     * must not rebuild — while an NTLM credential change must.
     */
    @Test
    void testOnlyNtlmAuthAffectsTheClient() throws Exception {
        withServer(url -> {
            ApacheHttpClient client = new ApacheHttpClient();
            KarateConfig config = new KarateConfig();
            config.configure("auth", Map.of("type", "basic", "username", "u", "password", "p"));
            client.apply(config);
            Object first = build(client, url);

            config.configure("auth", Map.of("type", "bearer", "token", "t"));
            client.apply(config);
            assertSame(first, built(client),
                    "apply() reads no non-NTLM auth setting, so switching between them must "
                            + "not tear down a live client");

            // Negative control: NTLM does reach the client, via its credentials provider.
            config.configure("ntlmAuth", Map.of("username", "u", "password", "p", "domain", "d"));
            client.apply(config);
            assertNull(built(client), "an NTLM credential change must rebuild");
        });
    }
}
