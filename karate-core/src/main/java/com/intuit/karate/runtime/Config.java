/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.driver.DockerTarget;
import com.intuit.karate.driver.Target;
import com.intuit.karate.http.HttpLogModifier;
import com.intuit.karate.server.Cookies;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Config {
    
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    public static final int DEFAULT_RETRY_INTERVAL = 3000;
    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_HIGHLIGHT_DURATION = 3000;

    private boolean sslEnabled = false;
    private String sslAlgorithm = "TLS";
    private String sslKeyStore;
    private String sslKeyStorePassword;
    private String sslKeyStoreType;
    private String sslTrustStore;
    private String sslTrustStorePassword;
    private String sslTrustStoreType;
    private boolean sslTrustAll = true;
    private boolean followRedirects = true;
    private int readTimeout = DEFAULT_TIMEOUT;
    private int connectTimeout = DEFAULT_TIMEOUT;
    private Charset charset = FileUtils.UTF8;
    private String proxyUri;
    private String proxyUsername;
    private String proxyPassword;
    private List<String> nonProxyHosts;
    private String localAddress;
    private int responseDelay;
    private boolean lowerCaseResponseHeaders = false;
    private boolean corsEnabled = false; // TODO deprecate
    private boolean logPrettyRequest;
    private boolean logPrettyResponse;
    private boolean printEnabled = true;
    private boolean outlineVariablesAuto = true;
    private boolean abortedStepsShouldPass = false;
    private Target driverTarget;
    private Map<String, Object> driverOptions;
    private Map<String, Object> robotOptions; // TODO make generic plugin model
    private HttpLogModifier logModifier;

    private Variable afterScenario = Variable.NULL;
    private Variable afterFeature = Variable.NULL;
    private Variable headers = Variable.NULL;
    private Variable cookies = Variable.NULL;
    private Variable responseHeaders = Variable.NULL;

    // retry config
    private int retryInterval = DEFAULT_RETRY_INTERVAL;
    private int retryCount = DEFAULT_RETRY_COUNT;

    // report config
    private boolean showLog = true;
    private boolean showAllSteps = true;

    // call single cache config
    private int callSingleCacheMinutes = 0;
    private String callSingleCacheDir = FileUtils.getBuildDir();

    public Config() {
        // zero arg constructor
    }

    private static <T> T get(Map<String, Object> map, String key, T defaultValue) {
        Object o = map.get(key);
        return o == null ? defaultValue : (T) o;
    }

    public boolean configure(String key, Variable value) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        switch (key) {
            case "headers":
                headers = value;
                return false;
            case "cookies":
                if (!value.isNull()) {
                    value = new Variable(Cookies.normalize(value.getValue()));
                }
                cookies = value;
                return false;
            case "responseHeaders":
                responseHeaders = value;
                return false;
            case "responseDelay":
                responseDelay = value.isNull() ? 0 : value.getAsInt();
                return false;
            case "lowerCaseResponseHeaders":
                lowerCaseResponseHeaders = value.isTrue();
                return false;
            case "cors":
                logger.warn("'cors' is deprecated, use server start api instead");
                corsEnabled = value.isTrue();
                return false;
            case "logPrettyResponse":
                logPrettyResponse = value.isTrue();
                return false;
            case "logPrettyRequest":
                logPrettyRequest = value.isTrue();
                return false;
            case "printEnabled":
                printEnabled = value.isTrue();
                return false;
            case "afterScenario":
                afterScenario = value;
                return false;
            case "afterFeature":
                afterFeature = value;
                return false;
            case "report":
                if (value.isMap()) {
                    Map<String, Object> map = value.getValue();
                    showLog = get(map, "showLog", showLog);
                    showAllSteps = get(map, "showAllSteps", showAllSteps);
                } else if (value.isTrue()) {
                    showLog = true;
                    showAllSteps = true;
                } else {
                    showLog = false;
                    showAllSteps = false;
                }
                return false;
            case "driver":
                driverOptions = value.getValue();
                return false;
            case "robot":
                robotOptions = value.getValue();
                return false;
            case "driverTarget":
                if (value.isMap()) {
                    Map<String, Object> map = value.getValue();
                    if (map.containsKey("docker")) {
                        driverTarget = new DockerTarget(map);
                    } else {
                        throw new RuntimeException("bad driverTarget config, expected key 'docker': " + map);
                    }
                } else {
                    driverTarget = value.getValue();
                }
                return false;
            case "retry":
                if (value.isMap()) {
                    Map<String, Object> map = value.getValue();
                    retryInterval = get(map, "interval", retryInterval);
                    retryCount = get(map, "count", retryCount);
                }
                return false;
            case "outlineVariablesAuto":
                outlineVariablesAuto = value.isTrue();
                return false;
            case "abortedStepsShouldPass":
                abortedStepsShouldPass = value.isTrue();
                return false;
            case "callSingleCache":
                if (value.isMap()) {
                    Map<String, Object> map = value.getValue();
                    callSingleCacheMinutes = get(map, "minutes", callSingleCacheMinutes);
                    callSingleCacheDir = get(map, "dir", callSingleCacheDir);
                }
                return false;
            case "charset":
                charset = value.isNull() ? null : Charset.forName(value.getAsString());
                return false;
            // here on the http client has to be re-constructed ================
            case "logModifier":
                logModifier = value.getValue();
                return true;
            case "ssl":
                if (value.isString()) {
                    sslEnabled = true;
                    sslAlgorithm = value.getAsString();
                } else if (value.isMap()) {
                    sslEnabled = true;
                    Map<String, Object> map = value.getValue();
                    sslKeyStore = (String) map.get("keyStore");
                    sslKeyStorePassword = (String) map.get("keyStorePassword");
                    sslKeyStoreType = (String) map.get("keyStoreType");
                    sslTrustStore = (String) map.get("trustStore");
                    sslTrustStorePassword = (String) map.get("trustStorePassword");
                    sslTrustStoreType = (String) map.get("trustStoreType");
                    Boolean trustAll = (Boolean) map.get("trustAll");
                    if (trustAll != null) {
                        sslTrustAll = trustAll;
                    }
                    sslAlgorithm = (String) map.get("algorithm");
                } else {
                    sslEnabled = value.isTrue();
                }
                return true;
            case "followRedirects":
                followRedirects = value.isTrue();
                return true;
            case "connectTimeout":
                connectTimeout = value.getAsInt();
                return true;
            case "readTimeout":
                readTimeout = value.getAsInt();
                return true;
            case "proxy":
                if (value == null) {
                    proxyUri = null;
                } else if (value.isString()) {
                    proxyUri = value.getAsString();
                } else {
                    Map<String, Object> map = value.getValue();
                    proxyUri = (String) map.get("uri");
                    proxyUsername = (String) map.get("username");
                    proxyPassword = (String) map.get("password");
                    nonProxyHosts = (List) map.get("nonProxyHosts");
                }
                return true;
            case "localAddress":
                localAddress = value.getAsString();
                return true;
            default:
                throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
    }

    public Config(Config parent) {
        sslEnabled = parent.sslEnabled;
        sslAlgorithm = parent.sslAlgorithm;
        sslTrustStore = parent.sslTrustStore;
        sslTrustStorePassword = parent.sslTrustStorePassword;
        sslTrustStoreType = parent.sslTrustStoreType;
        sslKeyStore = parent.sslKeyStore;
        sslKeyStorePassword = parent.sslKeyStorePassword;
        sslKeyStoreType = parent.sslKeyStoreType;
        sslTrustAll = parent.sslTrustAll;
        followRedirects = parent.followRedirects;
        readTimeout = parent.readTimeout;
        connectTimeout = parent.connectTimeout;
        charset = parent.charset;
        proxyUri = parent.proxyUri;
        proxyUsername = parent.proxyUsername;
        proxyPassword = parent.proxyPassword;
        nonProxyHosts = parent.nonProxyHosts;
        localAddress = parent.localAddress;
        responseDelay = parent.responseDelay;
        lowerCaseResponseHeaders = parent.lowerCaseResponseHeaders;
        corsEnabled = parent.corsEnabled;
        logPrettyRequest = parent.logPrettyRequest;
        logPrettyResponse = parent.logPrettyResponse;
        printEnabled = parent.printEnabled;
        driverOptions = parent.driverOptions;
        robotOptions = parent.robotOptions;
        driverTarget = parent.driverTarget;
        showLog = parent.showLog;
        showAllSteps = parent.showAllSteps;
        retryInterval = parent.retryInterval;
        retryCount = parent.retryCount;
        outlineVariablesAuto = parent.outlineVariablesAuto;
        abortedStepsShouldPass = parent.abortedStepsShouldPass;
        logModifier = parent.logModifier;
        callSingleCacheMinutes = parent.callSingleCacheMinutes;
        callSingleCacheDir = parent.callSingleCacheDir;
        headers = parent.headers;
        cookies = parent.cookies;
        responseHeaders = parent.responseHeaders;
        afterScenario = parent.afterScenario;
        afterFeature = parent.afterFeature;
    }

    public void setCookies(Variable cookies) {
        this.cookies = cookies;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public String getSslAlgorithm() {
        return sslAlgorithm;
    }

    public String getSslKeyStore() {
        return sslKeyStore;
    }

    public String getSslKeyStorePassword() {
        return sslKeyStorePassword;
    }

    public String getSslKeyStoreType() {
        return sslKeyStoreType;
    }

    public String getSslTrustStore() {
        return sslTrustStore;
    }

    public String getSslTrustStorePassword() {
        return sslTrustStorePassword;
    }

    public String getSslTrustStoreType() {
        return sslTrustStoreType;
    }

    public boolean isSslTrustAll() {
        return sslTrustAll;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getProxyUri() {
        return proxyUri;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public List<String> getNonProxyHosts() {
        return nonProxyHosts;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public Variable getHeaders() {
        return headers;
    }

    public Variable getCookies() {
        return cookies;
    }

    public Variable getResponseHeaders() {
        return responseHeaders;
    }

    public int getResponseDelay() {
        return responseDelay;
    }

    public boolean isLowerCaseResponseHeaders() {
        return lowerCaseResponseHeaders;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public boolean isLogPrettyRequest() {
        return logPrettyRequest;
    }

    public boolean isLogPrettyResponse() {
        return logPrettyResponse;
    }

    public boolean isPrintEnabled() {
        return printEnabled;
    }

    public Map<String, Object> getDriverOptions() {
        return driverOptions;
    }

    public Map<String, Object> getRobotOptions() {
        return robotOptions;
    }

    public Variable getAfterScenario() {
        return afterScenario;
    }

    public void setAfterScenario(Variable afterScenario) {
        this.afterScenario = afterScenario;
    }

    public Variable getAfterFeature() {
        return afterFeature;
    }

    public void setAfterFeature(Variable afterFeature) {
        this.afterFeature = afterFeature;
    }

    public boolean isShowLog() {
        return showLog;
    }

    public void setShowLog(boolean showLog) {
        this.showLog = showLog;
    }

    public boolean isShowAllSteps() {
        return showAllSteps;
    }

    public void setShowAllSteps(boolean showAllSteps) {
        this.showAllSteps = showAllSteps;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isOutlineVariablesAuto() {
        return outlineVariablesAuto;
    }

    public boolean isAbortedStepsShouldPass() {
        return abortedStepsShouldPass;
    }

    public Target getDriverTarget() {
        return driverTarget;
    }

    public void setDriverTarget(Target driverTarget) {
        this.driverTarget = driverTarget;
    }

    public HttpLogModifier getLogModifier() {
        return logModifier;
    }

    public String getCallSingleCacheDir() {
        return callSingleCacheDir;
    }

    public int getCallSingleCacheMinutes() {
        return callSingleCacheMinutes;
    }

}
