/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.http;

import com.intuit.karate.ScriptValue;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class HttpConfig {
    
    private boolean sslEnabled = false;
    private String sslAlgorithm = "TLS";
    private String sslTrustStore;
    private String sslTrustStorePassword;
    private String sslTrustStoreType;
    private boolean followRedirects = true;    
    private int readTimeout = 30000;
    private int connectTimeout = 30000;
    private String proxyUri;
    private String proxyUsername;
    private String proxyPassword;
    private ScriptValue headers = ScriptValue.NULL;
    private ScriptValue cookies = ScriptValue.NULL;
    private boolean logPrettyRequest;
    private boolean logPrettyResponse;
    private boolean printEnabled = true;    
    private String clientClass;
    private HttpClient clientInstance;
    private Map<String, Object> userDefined;
    private ScriptValue afterScenario = ScriptValue.NULL;
    private ScriptValue afterFeature = ScriptValue.NULL;
    
    public HttpConfig() {
        // zero arg constructor
    }
    
    public HttpConfig(HttpConfig parent) {
        sslEnabled = parent.sslEnabled;
        sslAlgorithm = parent.sslAlgorithm;
        sslTrustStore = parent.sslTrustStore;
        sslTrustStorePassword = parent.sslTrustStorePassword;
        sslTrustStoreType = parent.sslTrustStoreType;
        followRedirects = parent.followRedirects;        
        readTimeout = parent.readTimeout;
        connectTimeout = parent.connectTimeout;
        proxyUri = parent.proxyUri;
        proxyUsername = parent.proxyUsername;
        proxyPassword = parent.proxyPassword;
        headers = parent.headers;
        cookies = parent.cookies;
        logPrettyRequest = parent.logPrettyRequest;
        logPrettyResponse = parent.logPrettyResponse;
        printEnabled = parent.printEnabled;        
        clientClass = parent.clientClass;
        clientInstance = parent.clientInstance;
        userDefined = parent.userDefined;
        afterScenario = parent.afterScenario;
        afterFeature = parent.afterFeature;
    }
    
    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }        

    public String getSslAlgorithm() {
        return sslAlgorithm;
    }

    public void setSslAlgorithm(String sslAlgorithm) {
        this.sslAlgorithm = sslAlgorithm;
    }

    public String getSslTrustStore() {
        return sslTrustStore;
    }

    public void setSslTrustStore(String sslTrustStore) {
        this.sslTrustStore = sslTrustStore;
    }

    public String getSslTrustStorePassword() {
        return sslTrustStorePassword;
    }

    public void setSslTrustStorePassword(String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    public String getSslTrustStoreType() {
        return sslTrustStoreType;
    }

    public void setSslTrustStoreType(String sslTrustStoreType) {
        this.sslTrustStoreType = sslTrustStoreType;
    }        
    
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }    

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getProxyUri() {
        return proxyUri;
    }

    public void setProxyUri(String proxyUri) {
        this.proxyUri = proxyUri;
    }

    public String getProxyUsername() {
        return proxyUsername;
    }

    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public ScriptValue getHeaders() {
        return headers;
    }

    public void setHeaders(ScriptValue headers) {
        this.headers = headers;
    }

    public ScriptValue getCookies() {
        return cookies;
    }

    public void setCookies(ScriptValue cookies) {
        this.cookies = cookies;
    }        

    public boolean isLogPrettyRequest() {
        return logPrettyRequest;
    }

    public void setLogPrettyRequest(boolean logPrettyRequest) {
        this.logPrettyRequest = logPrettyRequest;
    }

    public boolean isLogPrettyResponse() {
        return logPrettyResponse;
    }

    public void setLogPrettyResponse(boolean logPrettyResponse) {
        this.logPrettyResponse = logPrettyResponse;
    }

    public boolean isPrintEnabled() {
        return printEnabled;
    }

    public void setPrintEnabled(boolean printEnabled) {
        this.printEnabled = printEnabled;
    }        

    public String getClientClass() {
        return clientClass;
    }

    public void setClientClass(String clientClass) {
        this.clientClass = clientClass;
    }

    public Map<String, Object> getUserDefined() {
        return userDefined;
    }

    public void setUserDefined(Map<String, Object> userDefined) {
        this.userDefined = userDefined;
    }        

    public HttpClient getClientInstance() {
        return clientInstance;
    }

    public void setClientInstance(HttpClient clientInstance) {
        this.clientInstance = clientInstance;
    }    

    public ScriptValue getAfterScenario() {
        return afterScenario;
    }

    public void setAfterScenario(ScriptValue afterScenario) {
        this.afterScenario = afterScenario;
    }

    public ScriptValue getAfterFeature() {
        return afterFeature;
    }

    public void setAfterFeature(ScriptValue afterFeature) {
        this.afterFeature = afterFeature;
    }    
    
}
