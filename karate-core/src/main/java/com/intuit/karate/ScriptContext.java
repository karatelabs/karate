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
package com.intuit.karate;

import com.intuit.karate.cucumber.ScenarioInfo;
import com.intuit.karate.cucumber.StepInterceptor;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.validator.Validator;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.slf4j.Logger;

/**
 *
 * @author pthomas3
 */
public class ScriptContext {

    public final Logger logger;

    protected final ScriptBindings bindings;

    protected final int callDepth;
    protected final List<String> tags;
    protected final Map<String, List<String>> tagValues;
    protected final ScriptValueMap vars;
    protected final Map<String, Validator> validators;
    protected final ScriptEnv env;
    protected final Consumer<Runnable> asyncSystem;
    protected final Runnable asyncNext;
    protected final StepInterceptor stepInterceptor;

    protected final ScenarioInfo scenarioInfo;

    // these can get re-built or swapped, so cannot be final
    protected HttpClient client;
    protected HttpConfig config;

    // the actual http request last sent on the wire
    protected HttpRequest prevRequest;           

    public void setScenarioError(Throwable error) {
        scenarioInfo.setErrorMessage(error.getMessage());
    }

    public void setPrevRequest(HttpRequest prevRequest) {
        this.prevRequest = prevRequest;
    }

    public HttpRequest getPrevRequest() {
        return prevRequest;
    }        

    public int getCallDepth() {
        return callDepth;
    }        

    public ScriptEnv getEnv() {
        return env;
    }

    public ScriptValueMap getVars() {
        return vars;
    }

    public HttpConfig getConfig() {
        return config;
    }

    public void updateConfigCookies(Map<String, Cookie> cookies) {
        if (cookies == null) {
            return;
        }
        if (config.getCookies().isNull()) {
            config.setCookies(new ScriptValue(cookies));
        } else {
            Map<String, Object> map = config.getCookies().evalAsMap(this);
            map.putAll(cookies);
            config.setCookies(new ScriptValue(map));
        }
    }

    public boolean isLogPrettyRequest() {
        return config.isLogPrettyRequest();
    }

    public boolean isLogPrettyResponse() {
        return config.isLogPrettyResponse();
    }

    public boolean isPrintEnabled() {
        return config.isPrintEnabled();
    }

    public ScriptContext(ScriptEnv env, CallContext call) {
        env = env.refresh(null);
        this.env = env; // make sure references below to env.env use the updated one
        logger = env.logger;
        callDepth = call.callDepth;
        asyncSystem = call.asyncSystem;
        stepInterceptor = call.stepInterceptor;
        asyncNext = call.asyncNext;
        tags = call.getTags();
        tagValues = call.getTagValues();
        scenarioInfo = call.getScenarioInfo();
        if (call.reuseParentContext) {
            vars = call.parentContext.vars; // shared context !
            validators = call.parentContext.validators;
            config = call.parentContext.config;
        } else if (call.parentContext != null) {
            vars = call.parentContext.vars.copy();
            validators = call.parentContext.validators;
            config = new HttpConfig(call.parentContext.config);
        } else {
            vars = new ScriptValueMap();
            validators = Validator.getDefaults();
            config = new HttpConfig();
            config.setClientClass(call.httpClientClass);
        }
        client = HttpClient.construct(config, this);
        bindings = new ScriptBindings(this);
        if (call.parentContext == null && call.evalKarateConfig) {
            String configDir = System.getProperty(ScriptBindings.KARATE_CONFIG_DIR);
            String configScript = ScriptBindings.readKarateConfigForEnv(true, configDir, null);
            try {                                                
                Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, configScript, null, this);
            } catch (Exception e) {
                if (e instanceof KarateFileNotFoundException) {
                    logger.warn("skipping bootstrap configuration: {}", e.getMessage());
                } else {
                    throw new RuntimeException("evaluation of '" + ScriptBindings.KARATE_CONFIG_JS + "' failed", e);
                }
            }
            if (env.env != null) {
                if (configDir == null) {
                    configDir = ScriptBindings.DOT_KARATE;
                }
                File configDirFile = new File(configDir);
                if (configDirFile.exists()) {
                    configScript =  ScriptBindings.readKarateConfigForEnv(false, configDir, env.env);
                    try {
                        Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, configScript, null, this);
                    } catch (Exception e) {
                        if (e instanceof KarateFileNotFoundException) {
                            logger.debug("skipping bootstrap configuration for env: {} - {}", env.env, e.getMessage());
                        } else {
                            throw new RuntimeException("evaluation of 'karate-config-" + env.env + ".js' failed", e);
                        }                        
                    }
                }
            }            
        }
        if (call.callArg != null) { // if call.reuseParentContext is true, arg will clobber parent context
            for (Map.Entry<String, Object> entry : call.callArg.entrySet()) {
                vars.put(entry.getKey(), entry.getValue());
            }
            vars.put(Script.VAR_ARG, call.callArg);
            vars.put(Script.VAR_LOOP, call.loopIndex);
        } else if (call.parentContext != null) {
            vars.put(Script.VAR_ARG, ScriptValue.NULL);
            vars.put(Script.VAR_LOOP, -1);
        }
        logger.trace("karate context init - initial properties: {}", vars);
    }

    public void configure(HttpConfig config) {
        this.config = config;
        client = HttpClient.construct(config, this);
    }

    public void configure(String key, String exp) {
        configure(key, Script.evalKarateExpression(exp, this));
    }

    public void configure(String key, ScriptValue value) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        if (key.equals("headers")) {
            config.setHeaders(value);
            return;
        }
        if (key.equals("cookies")) {
            config.setCookies(value);
            return;
        }
        if (key.equals("responseHeaders")) {
            config.setResponseHeaders(value);
            return;
        }
        if (key.equals("cors")) {
            config.setCorsEnabled(value.isBooleanTrue());
            return;
        }
        if (key.equals("logPrettyResponse")) {
            config.setLogPrettyResponse(value.isBooleanTrue());
            return;
        }
        if (key.equals("logPrettyRequest")) {
            config.setLogPrettyRequest(value.isBooleanTrue());
            return;
        }
        if (key.equals("printEnabled")) {
            config.setPrintEnabled(value.isBooleanTrue());
            return;
        }
        if (key.equals("afterScenario")) {
            config.setAfterScenario(value);
            return;
        }
        if (key.equals("afterFeature")) {
            config.setAfterFeature(value);
            return;
        }
        if (key.equals("httpClientClass")) {
            config.setClientClass(value.getAsString());
            // re-construct all the things ! and we exit early
            client = HttpClient.construct(config, this);
            return;
        }
        if (key.equals("httpClientInstance")) {
            config.setClientInstance(value.getValue(HttpClient.class));
            // here too, re-construct client - and exit early
            client = HttpClient.construct(config, this);
            return;
        }
        if (key.equals("charset")) {
            if (value.isNull()) {
                config.setCharset(null);
            } else {
                config.setCharset(Charset.forName(value.getAsString()));
            }
            // here again, re-construct client - and exit early
            client = HttpClient.construct(config, this);            
            return;
        }
        // beyond this point, we don't exit early and we have to re-configure the http client
        if (key.equals("ssl")) {
            if (value.isString()) {
                config.setSslEnabled(true);
                config.setSslAlgorithm(value.getAsString());
            } else if (value.isMapLike()) {
                config.setSslEnabled(true);
                Map<String, Object> map = value.getAsMap();
                config.setSslKeyStore((String) map.get("keyStore"));
                config.setSslKeyStorePassword((String) map.get("keyStorePassword"));
                config.setSslKeyStoreType((String) map.get("keyStoreType"));
                config.setSslTrustStore((String) map.get("trustStore"));
                config.setSslTrustStorePassword((String) map.get("trustStorePassword"));
                config.setSslTrustStoreType((String) map.get("trustStoreType"));
                String trustAll = (String) map.get("trustAll");
                if (trustAll != null) {
                    config.setSslTrustAll(Boolean.valueOf(trustAll));
                }
                config.setSslAlgorithm((String) map.get("algorithm"));
            } else {
                config.setSslEnabled(value.isBooleanTrue());
            }
        } else if (key.equals("followRedirects")) {
            config.setFollowRedirects(value.isBooleanTrue());
        } else if (key.equals("connectTimeout")) {
            config.setConnectTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("readTimeout")) {
            config.setReadTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("proxy")) {
            if (value.isString()) {
                config.setProxyUri(value.getAsString());
            } else {
                Map<String, Object> map = value.getAsMap();
                config.setProxyUri((String) map.get("uri"));
                config.setProxyUsername((String) map.get("username"));
                config.setProxyPassword((String) map.get("password"));
                config.setNonProxyHosts(((List)((ScriptObjectMirror)map.get("nonProxyHosts")).values()));
            }
        } else if (key.equals("userDefined")) {
            config.setUserDefined(value.getAsMap());
        } else {
            throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
        client.configure(config, this);
    }

}
