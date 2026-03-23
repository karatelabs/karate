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

/**
 * Tests for KarateConfig (configure keyword and karate.config getter).
 */
class StepConfigureTest {

    @Test
    void testConfigureSslBoolean() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure ssl = true
            * def cfg = karate.config
            * match cfg.sslEnabled == true
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureSslAlgorithm() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure ssl = 'TLSv1.2'
            * def cfg = karate.config
            * match cfg.sslEnabled == true
            * match cfg.sslAlgorithm == 'TLSv1.2'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureSslMap() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure ssl = { trustAll: false, algorithm: 'TLSv1.3' }
            * def cfg = karate.config
            * match cfg.sslEnabled == true
            * match cfg.sslTrustAll == false
            * match cfg.sslAlgorithm == 'TLSv1.3'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureProxyString() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure proxy = 'http://proxy.example.com:8080'
            * def cfg = karate.config
            * match cfg.proxyUri == 'http://proxy.example.com:8080'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureProxyMap() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure proxy = { uri: 'http://proxy:8080', username: 'user', password: 'pass' }
            * def cfg = karate.config
            * match cfg.proxyUri == 'http://proxy:8080'
            * match cfg.proxyUsername == 'user'
            * match cfg.proxyPassword == 'pass'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureTimeout() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure readTimeout = 60000
            * configure connectTimeout = 15000
            * def cfg = karate.config
            * match cfg.readTimeout == 60000
            * match cfg.connectTimeout == 15000
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureFollowRedirects() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure followRedirects = false
            * def cfg = karate.config
            * match cfg.followRedirects == false
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureRetry() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure retry = { interval: 5000, count: 5 }
            * def cfg = karate.config
            * match cfg.retryInterval == 5000
            * match cfg.retryCount == 5
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureReport() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure report = { showLog: false, showAllSteps: false }
            * def cfg = karate.config
            * match cfg.showLog == false
            * match cfg.showAllSteps == false
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureReportBoolean() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure report = false
            * def cfg = karate.config
            * match cfg.showLog == false
            * match cfg.showAllSteps == false
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureUnknownKeyThrows() {
        // Test directly on KarateConfig, not via feature execution
        KarateConfig config = new KarateConfig();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            config.configure("unknownKey", "value");
        });
        assertTrue(ex.getMessage().contains("unexpected 'configure' key"));
    }

    @Test
    void testConfigCopy() {
        // Test that karate.config returns a copy (mutations don't affect original)
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure readTimeout = 30000
            * def cfg1 = karate.config
            * configure readTimeout = 60000
            * def cfg2 = karate.config
            # cfg1 should still have old value (it's a copy)
            * match cfg1.readTimeout == 30000
            * match cfg2.readTimeout == 60000
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureUrl() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure url = 'http://example.com/api'
            * def cfg = karate.config
            * match cfg.url == 'http://example.com/api'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureCallSingleCache() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure callSingleCache = { minutes: 10, dir: '/tmp/cache' }
            * def cfg = karate.config
            * match cfg.callSingleCacheMinutes == 10
            * match cfg.callSingleCacheDir == '/tmp/cache'
            """);
        assertPassed(sr);
    }

    @Test
    void testKarateConfigDefaults() {
        KarateConfig config = new KarateConfig();
        assertEquals(30000, config.getReadTimeout());
        assertEquals(30000, config.getConnectTimeout());
        assertTrue(config.isFollowRedirects());
        assertFalse(config.isSslEnabled());
        assertEquals("TLS", config.getSslAlgorithm());
        assertTrue(config.isSslTrustAll());
        assertEquals(3000, config.getRetryInterval());
        assertEquals(3, config.getRetryCount());
        assertTrue(config.isShowLog());
        assertTrue(config.isShowAllSteps());
    }

    @Test
    void testKarateConfigCopy() {
        KarateConfig original = new KarateConfig();
        original.configure("ssl", true);
        original.configure("readTimeout", 60000);
        original.configure("proxy", "http://proxy:8080");

        KarateConfig copy = original.copy();

        // Verify copy has same values
        assertEquals(original.isSslEnabled(), copy.isSslEnabled());
        assertEquals(original.getReadTimeout(), copy.getReadTimeout());
        assertEquals(original.getProxyUri(), copy.getProxyUri());

        // Modify original, verify copy is unaffected
        original.configure("readTimeout", 90000);
        assertEquals(60000, copy.getReadTimeout());
        assertEquals(90000, original.getReadTimeout());
    }

    @Test
    void testSimpleObjectKeys() {
        KarateConfig config = new KarateConfig();
        assertTrue(config.jsKeys().contains("readTimeout"));
        assertTrue(config.jsKeys().contains("ssl"));  // Map-based grouped settings
        assertTrue(config.jsKeys().contains("proxy"));
        assertTrue(config.jsKeys().contains("auth"));
        assertTrue(config.jsKeys().contains("headers"));
    }

    @Test
    void testSimpleObjectJsGet() {
        KarateConfig config = new KarateConfig();
        config.configure("readTimeout", 45000);
        config.configure("ssl", true);

        assertEquals(45000, config.jsGet("readTimeout"));
        assertEquals(true, config.jsGet("sslEnabled"));
        assertEquals(30000, config.jsGet("connectTimeout")); // default
    }

    // ========== Driver Configuration ==========

    @Test
    void testConfigureDriverMap() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure driver = { headless: true, timeout: 60000 }
            * def cfg = karate.config
            * match cfg.driverConfig.headless == true
            * match cfg.driverConfig.timeout == 60000
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureDriverEmptyMap() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure driver = {}
            * def cfg = karate.config
            * match cfg.driverConfig != null
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureDriverWebSocketUrl() {
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure driver = { webSocketUrl: 'ws://localhost:9222/devtools/page/1234' }
            * def cfg = karate.config
            * match cfg.driverConfig.webSocketUrl == 'ws://localhost:9222/devtools/page/1234'
            """);
        assertPassed(sr);
    }

    @Test
    void testDriverConfigCopy() {
        KarateConfig original = new KarateConfig();
        original.configure("driver", Map.of("headless", true, "timeout", 30000));

        KarateConfig copy = original.copy();

        // Verify copy has driver config
        assertNotNull(copy.getDriverConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> driverConfig = (Map<String, Object>) copy.getDriverConfig();
        assertEquals(true, driverConfig.get("headless"));
        assertEquals(30000, driverConfig.get("timeout"));
    }

    @Test
    void testDriverConfigJsGet() {
        KarateConfig config = new KarateConfig();
        config.configure("driver", Map.of("headless", true));

        Object driverConfig = config.jsGet("driverConfig");
        assertNotNull(driverConfig);
        assertTrue(driverConfig instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) driverConfig;
        assertEquals(true, map.get("headless"));
    }

    @Test
    void testConfigureAfterFeature() {
        // Test that configure afterFeature is accepted (no error)
        KarateConfig config = new KarateConfig();
        config.configure("afterFeature", "function() { }");
        assertNotNull(config.getAfterFeature());
    }

    @Test
    void testAfterFeatureInConfig() {
        // Verify afterFeature is accessible via karate.config
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * configure afterFeature = function() { karate.log('hook called') }
            * def cfg = karate.config
            * match cfg.afterFeature == '#present'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureSslWithEmbeddedExpressions() {
        // Test that embedded expressions like '#(varName)' work with ssl configure
        // (previously only headers, cookies, and auth processed embedded expressions)
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * def myAlgorithm = 'TLSv1.2'
            * configure ssl = { algorithm: '#(myAlgorithm)' }
            * def cfg = karate.config
            * match cfg.sslEnabled == true
            * match cfg.sslAlgorithm == 'TLSv1.2'
            """);
        assertPassed(sr);
    }

    @Test
    void testConfigureProxyWithEmbeddedExpressions() {
        // Test that embedded expressions work with proxy configure
        ScenarioRuntime sr = run("""
            Feature:
            Scenario:
            * def proxyHost = 'proxy.example.com'
            * def proxyPort = '8080'
            * configure proxy = { uri: '#(proxyHost + \":\" + proxyPort)' }
            * def cfg = karate.config
            * match cfg.proxyUri == 'proxy.example.com:8080'
            """);
        assertPassed(sr);
    }

}
