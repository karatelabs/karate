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

import com.intuit.karate.exception.KarateFileNotFoundException;
import com.intuit.karate.http.Cookie;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.validator.Validator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 *
 * @author pthomas3
 */
public class ScriptContext {

    public final Logger logger;

    private static final String KARATE_DOT_CONTEXT = "karate.context";
    public static final String KARATE_NAME = "karate";
    private static final String VAR_READ = "read";

    protected final List<String> tags;
    protected final Map<String, List<String>> tagValues;
    protected final ScriptValueMap vars;
    protected final Map<String, Validator> validators;
    protected final ScriptEnv env;    
    private final ScriptValue readFunction;

    // these can get re-built or swapped, so cannot be final
    protected HttpClient client;
    protected HttpConfig config;

    public ScriptEnv getEnv() {
        return env;
    }        

    public ScriptValueMap getVars() {
        return vars;
    }

    public ScriptValue getConfigHeaders() {
        return config.getHeaders();
    }

    public ScriptValue getConfigCookies() {
        return config.getCookies();
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
        this.env = env.refresh(null);
        logger = env.logger;
        tags = call.getTags();
        tagValues = call.getTagValues();
        if (call.parentContext != null) {
            vars = Script.clone(call.parentContext.vars);
            validators = call.parentContext.validators;
            config = new HttpConfig(call.parentContext.config);
        } else {
            vars = new ScriptValueMap();
            validators = Script.getDefaultValidators();
            config = new HttpConfig();
        }
        client = HttpClient.construct(config, this);
        readFunction = Script.eval(getFileReaderFunction(), this);
        if (call.parentContext == null && call.evalKarateConfig) {
            try {
                Script.callAndUpdateConfigAndAlsoVarsIfMapReturned(false, "read('classpath:karate-config.js')", null, this);
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof KarateFileNotFoundException) {
                    logger.warn("karate-config.js not found on the classpath, skipping bootstrap configuration");
                } else {
                    throw new RuntimeException("bootstrap configuration error, evaluation of karate-config.js failed:", cause);
                }
            }
        }
        if (call.callArg != null) {
            for (Map.Entry<String, Object> entry : call.callArg.entrySet()) {
                vars.put(entry.getKey(), entry.getValue());
            }
        }
        logger.trace("karate context init - initial properties: {}", vars);
    }

    private static String getFileReaderFunction() {
        return "function(path) {\n"
                + "  var FileUtils = Java.type('" + FileUtils.class.getCanonicalName() + "');\n"
                + "  return FileUtils.readFile(path, " + KARATE_DOT_CONTEXT + ").value;\n"
                + "}";
    }

    public void configure(HttpConfig config) {
        this.config = config;
        client = HttpClient.construct(config, this);
    }    
    
    public void configure(String key, String exp) {
        configure(key, Script.eval(exp, this));
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
        // beyond this point, we don't exit early and we have to re-configure the http client
        if (key.equals("ssl")) {
            if (value.isString()) {
                config.setSslEnabled(true);
                config.setSslAlgorithm(value.getAsString());
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
            }
        } else if (key.equals("userDefined")) {
            config.setUserDefined(value.getAsMap());
        } else {
            throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
        client.configure(config, this);
    }

    public Map<String, Object> getVariableBindings() {
        Map<String, Object> map = Script.simplify(vars);
        if (readFunction != null) {
            map.put(VAR_READ, readFunction.getValue());
        }
        return map;
    }

}
