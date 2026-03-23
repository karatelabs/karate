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

import io.karatelabs.js.SimpleObject;
import org.slf4j.Logger;
import io.karatelabs.output.LogContext;
import io.karatelabs.output.LogLevel;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds runtime configuration settings set via the 'configure' keyword.
 * <p>
 * This is distinct from:
 * <ul>
 *   <li>{@link KaratePom} - Project file (karate-pom.json) for CLI options</li>
 *   <li>karate-config.js - Runtime bootstrap that runs per-scenario</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * * configure ssl = true
 * * configure proxy = 'http://proxy:8080'
 * * configure readTimeout = 60000
 * * def cfg = karate.config
 * * match cfg.sslEnabled == true
 * </pre>
 * <p>
 * Implements {@link SimpleObject} so it can be accessed as a JavaScript object
 * via {@code karate.config}.
 */
public class KarateConfig implements SimpleObject {

    private static final Logger logger = LogContext.RUNTIME_LOGGER;

    // Keys exposed via SimpleObject
    private static final List<String> KEYS = List.of(
            // HTTP client settings
            "url", "readTimeout", "connectTimeout", "followRedirects", "localAddress", "charset",
            // Grouped settings (Maps)
            "ssl", "proxy", "auth", "retry", "report", "callSingleCache",
            // Headers/Cookies
            "headers", "cookies",
            // HTTP retry
            "httpRetryEnabled",
            // Execution control
            "continueOnStepFailure", "abortedStepsShouldPass", "abortSuiteOnFailure", "matchEachEmptyAllowed",
            // Mock settings
            "corsEnabled", "responseHeaders", "afterScenario", "afterScenarioOutline", "afterFeature",
            // Driver
            "driverConfig"
    );

    // ===== HTTP Client (requires rebuild when changed) =====
    private String url;
    private int readTimeout = 30000;
    private int connectTimeout = 30000;
    private boolean followRedirects = true;
    private String localAddress;
    private Charset charset = StandardCharsets.UTF_8;

    // ===== Grouped Settings (Maps) =====

    // SSL configuration: { enabled, algorithm, keyStore, keyStorePassword, keyStoreType,
    //                      trustStore, trustStorePassword, trustStoreType, trustAll }
    private Map<String, Object> ssl = new HashMap<>();

    // Proxy configuration: { uri, username, password, nonProxyHosts }
    private Map<String, Object> proxy = new HashMap<>();

    // Auth: { type, username, password, token, accessTokenUrl, clientId, clientSecret, scope, domain, workstation }
    // Supported types: basic, bearer, oauth2, ntlm
    private Map<String, Object> auth = new HashMap<>();

    // Retry: { interval, count }
    private Map<String, Object> retry = new HashMap<>(Map.of("interval", 3000, "count", 3));

    // Report: { showLog, showAllSteps, logLevel }
    private Map<String, Object> report = new HashMap<>(Map.of("showLog", true, "showAllSteps", true));

    // CallSingleCache: { minutes, dir }
    private Map<String, Object> callSingleCache = new HashMap<>();

    // ===== Non-HTTP (no client rebuild needed) =====
    private Object headers;  // Map<String,Object> or JS function
    private Object cookies;  // Map<String,Object>

    // HTTP retry (separate from retry config because it requires client rebuild)
    private boolean httpRetryEnabled;

    // Execution control
    private boolean continueOnStepFailure;
    private boolean abortedStepsShouldPass;
    private boolean abortSuiteOnFailure;
    private boolean matchEachEmptyAllowed;

    // Mock settings
    private boolean corsEnabled;
    private Object responseHeaders;  // Map<String,Object> or JS function
    private Object afterScenario;         // Invokable - hook called after each scenario
    private Object afterScenarioOutline;  // Invokable - hook called after all examples of an outline complete
    private Object afterFeature;          // Invokable - hook called after feature completes

    // Driver configuration (Map or DriverOptions)
    private Object driverConfig;

    /**
     * Create a deep copy of this configuration.
     * Used to snapshot config state for callonce/callSingle isolation.
     *
     * @return a new KarateConfig with copied values
     */
    public KarateConfig copy() {
        KarateConfig copy = new KarateConfig();
        copy.copyFrom(this);
        return copy;
    }

    /**
     * Copy all configuration values from another KarateConfig.
     * Used to propagate config changes from called features back to the caller.
     *
     * @param other the source config to copy from
     */
    public void copyFrom(KarateConfig other) {
        if (other == null) return;
        // HTTP client settings
        this.url = other.url;
        this.readTimeout = other.readTimeout;
        this.connectTimeout = other.connectTimeout;
        this.followRedirects = other.followRedirects;
        this.localAddress = other.localAddress;
        this.charset = other.charset;
        // Grouped settings (deep copy Maps)
        this.ssl = new HashMap<>(other.ssl);
        this.proxy = new HashMap<>(other.proxy);
        if (other.proxy.containsKey("nonProxyHosts") && other.proxy.get("nonProxyHosts") instanceof List<?> list) {
            this.proxy.put("nonProxyHosts", new ArrayList<>(list));
        }
        this.auth = new HashMap<>(other.auth);
        this.retry = new HashMap<>(other.retry);
        this.report = new HashMap<>(other.report);
        this.callSingleCache = new HashMap<>(other.callSingleCache);
        // Headers/Cookies (shallow copy - could be function refs)
        this.headers = other.headers;
        this.cookies = other.cookies;
        // HTTP retry
        this.httpRetryEnabled = other.httpRetryEnabled;
        // Execution control
        this.continueOnStepFailure = other.continueOnStepFailure;
        this.abortedStepsShouldPass = other.abortedStepsShouldPass;
        this.abortSuiteOnFailure = other.abortSuiteOnFailure;
        this.matchEachEmptyAllowed = other.matchEachEmptyAllowed;
        // Mock settings
        this.corsEnabled = other.corsEnabled;
        this.responseHeaders = other.responseHeaders;
        this.afterScenario = other.afterScenario;
        this.afterScenarioOutline = other.afterScenarioOutline;
        this.afterFeature = other.afterFeature;
        // Driver
        this.driverConfig = other.driverConfig;
    }

    /**
     * Apply configuration from a key-value pair.
     *
     * @param key   the configure key (e.g., "ssl", "proxy", "readTimeout")
     * @param value the value to set
     * @return true if HTTP client needs to be rebuilt, false otherwise
     * @throws RuntimeException if key is not recognized
     */
    @SuppressWarnings("unchecked")
    public boolean configure(String key, Object value) {
        key = key != null ? key.trim() : "";
        return switch (key) {
            // HTTP client settings (require rebuild)
            case "ssl" -> {
                configureSsl(value);
                yield true;
            }
            case "proxy" -> {
                configureProxy(value);
                yield true;
            }
            case "readTimeout" -> {
                this.readTimeout = toInt(value);
                yield true;
            }
            case "connectTimeout" -> {
                this.connectTimeout = toInt(value);
                yield true;
            }
            case "followRedirects" -> {
                this.followRedirects = toBoolean(value);
                yield true;
            }
            case "localAddress" -> {
                this.localAddress = toString(value);
                yield true;
            }
            case "charset" -> {
                // null value means disable auto-charset (V1 compatibility)
                this.charset = value == null ? null : Charset.forName(toString(value));
                yield true;
            }
            case "ntlmAuth" -> {
                // Legacy support: convert ntlmAuth to auth with type: 'ntlm'
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> ntlm = new HashMap<>();
                    ntlm.put("type", "ntlm");
                    ntlm.putAll((Map<String, Object>) map);
                    configureAuth(ntlm);
                } else if (value == null) {
                    configureAuth(null);
                }
                yield true;  // NTLM requires HTTP client rebuild
            }
            case "auth" -> {
                configureAuth(value);
                // NTLM requires client rebuild, others don't
                String type = value instanceof Map<?, ?> m ? toString(m.get("type")) : null;
                yield "ntlm".equals(type);
            }

            // Non-HTTP settings (no rebuild)
            case "url" -> {
                this.url = toString(value);
                yield false;
            }
            case "headers" -> {
                this.headers = value;
                yield false;
            }
            case "cookies" -> {
                this.cookies = value;
                yield false;
            }
            case "retry" -> {
                configureRetry(value);
                yield false;
            }
            case "httpRetryEnabled" -> {
                this.httpRetryEnabled = toBoolean(value);
                yield true; // Requires HTTP client rebuild
            }
            case "report" -> {
                configureReport(value);
                yield false;
            }
            case "callSingleCache" -> {
                configureCallSingleCache(value);
                yield false;
            }
            case "continueOnStepFailure" -> {
                this.continueOnStepFailure = toBoolean(value);
                yield false;
            }
            case "abortedStepsShouldPass" -> {
                this.abortedStepsShouldPass = toBoolean(value);
                yield false;
            }
            case "abortSuiteOnFailure" -> {
                this.abortSuiteOnFailure = toBoolean(value);
                yield false;
            }
            case "matchEachEmptyAllowed" -> {
                this.matchEachEmptyAllowed = toBoolean(value);
                yield false;
            }

            // Mock settings
            case "cors" -> {
                this.corsEnabled = toBoolean(value);
                yield false;
            }
            case "responseHeaders" -> {
                this.responseHeaders = value;
                yield false;
            }
            case "afterScenario" -> {
                this.afterScenario = value;
                yield false;
            }
            case "afterScenarioOutline" -> {
                this.afterScenarioOutline = value;
                yield false;
            }
            case "afterFeature" -> {
                this.afterFeature = value;
                yield false;
            }

            // Driver configuration
            case "driver" -> {
                this.driverConfig = value;
                yield false;
            }

            // Deprecated v1 options - no-op with warning
            case "logPrettyRequest", "logPrettyResponse", "printEnabled",
                 "lowerCaseResponseHeaders", "logModifier" -> {
                logger.warn("configure '{}' is deprecated and has no effect in v2", key);
                yield false;
            }

            default -> throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        };
    }

    private void configureSsl(Object value) {
        if (value == null) {
            this.ssl.clear();
            return;
        }
        if (value instanceof Boolean b) {
            this.ssl.clear();
            this.ssl.put("enabled", b);
            this.ssl.put("trustAll", true);
            return;
        }
        if (value instanceof String s) {
            this.ssl.clear();
            this.ssl.put("enabled", true);
            this.ssl.put("algorithm", s);
            this.ssl.put("trustAll", true);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            this.ssl.clear();
            this.ssl.put("enabled", true);
            if (map.containsKey("algorithm")) {
                this.ssl.put("algorithm", toString(map.get("algorithm")));
            }
            if (map.containsKey("keyStore")) {
                this.ssl.put("keyStore", toString(map.get("keyStore")));
            }
            if (map.containsKey("keyStorePassword")) {
                this.ssl.put("keyStorePassword", toString(map.get("keyStorePassword")));
            }
            if (map.containsKey("keyStoreType")) {
                this.ssl.put("keyStoreType", toString(map.get("keyStoreType")));
            }
            if (map.containsKey("trustStore")) {
                this.ssl.put("trustStore", toString(map.get("trustStore")));
            }
            if (map.containsKey("trustStorePassword")) {
                this.ssl.put("trustStorePassword", toString(map.get("trustStorePassword")));
            }
            if (map.containsKey("trustStoreType")) {
                this.ssl.put("trustStoreType", toString(map.get("trustStoreType")));
            }
            if (map.containsKey("trustAll")) {
                this.ssl.put("trustAll", toBoolean(map.get("trustAll")));
            } else {
                this.ssl.put("trustAll", true);  // default
            }
        }
    }

    private void configureProxy(Object value) {
        if (value == null) {
            this.proxy.clear();
            return;
        }
        if (value instanceof String s) {
            this.proxy.clear();
            this.proxy.put("uri", s);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            this.proxy.clear();
            if (map.containsKey("uri")) {
                this.proxy.put("uri", toString(map.get("uri")));
            }
            if (map.containsKey("username")) {
                this.proxy.put("username", toString(map.get("username")));
            }
            if (map.containsKey("password")) {
                this.proxy.put("password", toString(map.get("password")));
            }
            if (map.containsKey("nonProxyHosts")) {
                Object nph = map.get("nonProxyHosts");
                if (nph instanceof List<?> list) {
                    List<String> hosts = new ArrayList<>();
                    for (Object item : list) {
                        hosts.add(toString(item));
                    }
                    this.proxy.put("nonProxyHosts", hosts);
                }
            }
        }
    }

    private void configureAuth(Object value) {
        if (value == null) {
            this.auth.clear();
            return;
        }
        if (value instanceof Map<?, ?> map) {
            this.auth.clear();
            String type = toString(map.get("type"));
            this.auth.put("type", type);

            switch (type != null ? type : "") {
                case "basic" -> {
                    this.auth.put("username", toString(map.get("username")));
                    this.auth.put("password", toString(map.get("password")));
                }
                case "bearer" -> {
                    this.auth.put("token", toString(map.get("token")));
                }
                case "oauth2" -> {
                    this.auth.put("grantType", toString(map.get("grantType")));
                    this.auth.put("accessTokenUrl", toString(map.get("accessTokenUrl")));
                    this.auth.put("clientId", toString(map.get("clientId")));
                    this.auth.put("clientSecret", toString(map.get("clientSecret")));
                    if (map.containsKey("scope")) {
                        this.auth.put("scope", toString(map.get("scope")));
                    }
                }
                case "ntlm" -> {
                    this.auth.put("username", toString(map.get("username")));
                    this.auth.put("password", toString(map.get("password")));
                    if (map.containsKey("domain")) {
                        this.auth.put("domain", toString(map.get("domain")));
                    }
                    if (map.containsKey("workstation")) {
                        this.auth.put("workstation", toString(map.get("workstation")));
                    }
                }
            }
        }
    }

    private void configureRetry(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("interval")) {
                this.retry.put("interval", toInt(map.get("interval")));
            }
            if (map.containsKey("count")) {
                this.retry.put("count", toInt(map.get("count")));
            }
        }
    }

    private void configureReport(Object value) {
        if (value instanceof Boolean b) {
            this.report.put("showLog", b);
            this.report.put("showAllSteps", b);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("showLog")) {
                this.report.put("showLog", toBoolean(map.get("showLog")));
            }
            if (map.containsKey("showAllSteps")) {
                this.report.put("showAllSteps", toBoolean(map.get("showAllSteps")));
            }
            if (map.containsKey("logLevel")) {
                String levelStr = toString(map.get("logLevel"));
                this.report.put("logLevel", levelStr);
                LogLevel level = LogLevel.valueOf(levelStr.toUpperCase());
                LogContext.setLogLevel(level);
            }
        }
    }

    private void configureCallSingleCache(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("minutes")) {
                this.callSingleCache.put("minutes", toInt(map.get("minutes")));
            }
            if (map.containsKey("dir")) {
                this.callSingleCache.put("dir", toString(map.get("dir")));
            }
        }
    }

    // ===== Type conversion helpers =====

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(value.toString());
    }

    // ===== SimpleObject implementation =====

    @Override
    public Collection<String> jsKeys() {
        return KEYS;
    }

    @Override
    public Object jsGet(String name) {
        return switch (name) {
            case "url" -> url;
            case "readTimeout" -> readTimeout;
            case "connectTimeout" -> connectTimeout;
            case "followRedirects" -> followRedirects;
            case "localAddress" -> localAddress;
            case "charset" -> charset != null ? charset.name() : null;
            // Grouped settings
            case "ssl" -> ssl;
            case "proxy" -> proxy;
            case "ntlmAuth" -> "ntlm".equals(auth.get("type")) ? auth : null;
            case "auth" -> auth;
            case "retry" -> retry;
            case "report" -> report;
            case "callSingleCache" -> callSingleCache;
            // Legacy accessors (for backward compatibility)
            case "sslEnabled" -> ssl.getOrDefault("enabled", false);
            case "sslAlgorithm" -> ssl.getOrDefault("algorithm", "TLS");
            case "sslKeyStore" -> ssl.get("keyStore");
            case "sslKeyStorePassword" -> ssl.get("keyStorePassword");
            case "sslKeyStoreType" -> ssl.get("keyStoreType");
            case "sslTrustStore" -> ssl.get("trustStore");
            case "sslTrustStorePassword" -> ssl.get("trustStorePassword");
            case "sslTrustStoreType" -> ssl.get("trustStoreType");
            case "sslTrustAll" -> ssl.getOrDefault("trustAll", true);
            case "proxyUri" -> proxy.get("uri");
            case "proxyUsername" -> proxy.get("username");
            case "proxyPassword" -> proxy.get("password");
            case "nonProxyHosts" -> proxy.get("nonProxyHosts");
            case "ntlmUsername" -> "ntlm".equals(auth.get("type")) ? auth.get("username") : null;
            case "ntlmPassword" -> "ntlm".equals(auth.get("type")) ? auth.get("password") : null;
            case "ntlmDomain" -> "ntlm".equals(auth.get("type")) ? auth.get("domain") : null;
            case "ntlmWorkstation" -> "ntlm".equals(auth.get("type")) ? auth.get("workstation") : null;
            case "authType" -> auth.get("type");
            case "authUsername" -> auth.get("username");
            case "authPassword" -> auth.get("password");
            case "authToken" -> auth.get("token");
            case "authAccessTokenUrl" -> auth.get("accessTokenUrl");
            case "authClientId" -> auth.get("clientId");
            case "authClientSecret" -> auth.get("clientSecret");
            case "authScope" -> auth.get("scope");
            case "retryInterval" -> retry.getOrDefault("interval", 3000);
            case "retryCount" -> retry.getOrDefault("count", 3);
            case "showLog" -> report.getOrDefault("showLog", true);
            case "showAllSteps" -> report.getOrDefault("showAllSteps", true);
            case "callSingleCacheMinutes" -> callSingleCache.getOrDefault("minutes", 0);
            case "callSingleCacheDir" -> callSingleCache.get("dir");
            // Other settings
            case "headers" -> headers;
            case "cookies" -> cookies;
            case "httpRetryEnabled" -> httpRetryEnabled;
            case "continueOnStepFailure" -> continueOnStepFailure;
            case "abortedStepsShouldPass" -> abortedStepsShouldPass;
            case "abortSuiteOnFailure" -> abortSuiteOnFailure;
            case "matchEachEmptyAllowed" -> matchEachEmptyAllowed;
            case "corsEnabled" -> corsEnabled;
            case "responseHeaders" -> responseHeaders;
            case "afterScenario" -> afterScenario;
            case "afterScenarioOutline" -> afterScenarioOutline;
            case "afterFeature" -> afterFeature;
            case "driverConfig" -> driverConfig;
            default -> null;
        };
    }

    // ===== Getters =====

    public String getUrl() {
        return url;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public Charset getCharset() {
        return charset;
    }

    // ===== SSL Getters =====

    public Map<String, Object> getSsl() {
        return ssl;
    }

    public boolean isSslEnabled() {
        return toBoolean(ssl.get("enabled"));
    }

    public String getSslAlgorithm() {
        Object val = ssl.get("algorithm");
        return val != null ? val.toString() : "TLS";
    }

    public String getSslKeyStore() {
        return toString(ssl.get("keyStore"));
    }

    public String getSslKeyStorePassword() {
        return toString(ssl.get("keyStorePassword"));
    }

    public String getSslKeyStoreType() {
        return toString(ssl.get("keyStoreType"));
    }

    public String getSslTrustStore() {
        return toString(ssl.get("trustStore"));
    }

    public String getSslTrustStorePassword() {
        return toString(ssl.get("trustStorePassword"));
    }

    public String getSslTrustStoreType() {
        return toString(ssl.get("trustStoreType"));
    }

    public boolean isSslTrustAll() {
        Object val = ssl.get("trustAll");
        return val == null || toBoolean(val);  // default true
    }

    // ===== Proxy Getters =====

    public Map<String, Object> getProxy() {
        return proxy;
    }

    public String getProxyUri() {
        return toString(proxy.get("uri"));
    }

    public String getProxyUsername() {
        return toString(proxy.get("username"));
    }

    public String getProxyPassword() {
        return toString(proxy.get("password"));
    }

    @SuppressWarnings("unchecked")
    public List<String> getNonProxyHosts() {
        Object val = proxy.get("nonProxyHosts");
        return val instanceof List ? (List<String>) val : null;
    }

    // ===== NTLM Getters (legacy - reads from auth when type is 'ntlm') =====

    public Map<String, Object> getNtlmAuth() {
        return "ntlm".equals(auth.get("type")) ? auth : null;
    }

    public String getNtlmUsername() {
        return "ntlm".equals(auth.get("type")) ? toString(auth.get("username")) : null;
    }

    public String getNtlmPassword() {
        return "ntlm".equals(auth.get("type")) ? toString(auth.get("password")) : null;
    }

    public String getNtlmDomain() {
        return "ntlm".equals(auth.get("type")) ? toString(auth.get("domain")) : null;
    }

    public String getNtlmWorkstation() {
        return "ntlm".equals(auth.get("type")) ? toString(auth.get("workstation")) : null;
    }

    // ===== Auth Getters =====

    public Map<String, Object> getAuth() {
        return auth;
    }

    public String getAuthType() {
        return toString(auth.get("type"));
    }

    public String getAuthUsername() {
        return toString(auth.get("username"));
    }

    public String getAuthPassword() {
        return toString(auth.get("password"));
    }

    public String getAuthToken() {
        return toString(auth.get("token"));
    }

    public String getAuthAccessTokenUrl() {
        return toString(auth.get("accessTokenUrl"));
    }

    public String getAuthClientId() {
        return toString(auth.get("clientId"));
    }

    public String getAuthClientSecret() {
        return toString(auth.get("clientSecret"));
    }

    public String getAuthScope() {
        return toString(auth.get("scope"));
    }

    // ===== Retry Getters =====

    public Map<String, Object> getRetry() {
        return retry;
    }

    public int getRetryInterval() {
        return toInt(retry.getOrDefault("interval", 3000));
    }

    public int getRetryCount() {
        return toInt(retry.getOrDefault("count", 3));
    }

    // ===== Report Getters =====

    public Map<String, Object> getReport() {
        return report;
    }

    public boolean isShowLog() {
        return toBoolean(report.getOrDefault("showLog", true));
    }

    public boolean isShowAllSteps() {
        return toBoolean(report.getOrDefault("showAllSteps", true));
    }

    // ===== CallSingleCache Getters =====

    public Map<String, Object> getCallSingleCache() {
        return callSingleCache;
    }

    public int getCallSingleCacheMinutes() {
        return toInt(callSingleCache.getOrDefault("minutes", 0));
    }

    public String getCallSingleCacheDir() {
        return toString(callSingleCache.get("dir"));
    }

    // ===== Other Getters =====

    public Object getHeaders() {
        return headers;
    }

    public Object getCookies() {
        return cookies;
    }

    public boolean isHttpRetryEnabled() {
        return httpRetryEnabled;
    }

    public boolean isContinueOnStepFailure() {
        return continueOnStepFailure;
    }

    public boolean isAbortedStepsShouldPass() {
        return abortedStepsShouldPass;
    }

    public boolean isAbortSuiteOnFailure() {
        return abortSuiteOnFailure;
    }

    public boolean isMatchEachEmptyAllowed() {
        return matchEachEmptyAllowed;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public Object getResponseHeaders() {
        return responseHeaders;
    }

    public Object getAfterScenario() {
        return afterScenario;
    }

    public Object getAfterScenarioOutline() {
        return afterScenarioOutline;
    }

    public Object getAfterFeature() {
        return afterFeature;
    }

    public Object getDriverConfig() {
        return driverConfig;
    }

}
