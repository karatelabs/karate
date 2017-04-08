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

import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.validator.Validator;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScriptContext {

    private static final Logger logger = LoggerFactory.getLogger(ScriptContext.class);

    private static final String KARATE_DOT_CONTEXT = "karate.context";
    public static final String KARATE_NAME = "karate";
    private static final String VAR_READ = "read";

    protected final ScriptValueMap vars;

    protected HttpClient client;
    protected final Map<String, Validator> validators;
    protected final ScriptEnv env;

    // stateful config
    private ScriptValue headers = ScriptValue.NULL;
    private ScriptValue readFunction;
    private HttpConfig config;

    public ScriptValueMap getVars() {
        return vars;
    }

    public ScriptValue getConfiguredHeaders() {
        return headers;
    }        

    public ScriptContext(ScriptEnv env, ScriptContext parent, Map<String, Object> arg) {
        this.env = env.refresh();
        if (parent != null) {
            vars = Script.clone(parent.vars);
            readFunction = parent.readFunction;
            validators = parent.validators;
            headers = parent.headers;
            config = parent.config;
            if (arg != null) {
                for (Map.Entry<String, Object> entry : arg.entrySet()) {
                    vars.put(entry.getKey(), entry.getValue());
                }
            }
            client = HttpClient.construct();
            client.configure(config);            
        } else {
            vars = new ScriptValueMap();
            validators = Script.getDefaultValidators();
            readFunction = Script.eval(getFileReaderFunction(), this);
            config = new HttpConfig();
            // needs to be done before karate-config as it can call 'configure'
            client = HttpClient.construct();
            client.configure(config);            
            try {
                Script.callAndUpdateVars("read('classpath:karate-config.js')", null, this);
            } catch (Exception e) {
                logger.warn("start-up configuration failed, missing or bad 'karate-config.js'", e);
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
    
    public void configure(String key, String exp) {
        configure(key, Script.eval(exp, this));
    }

    public void configure(String key, ScriptValue value) { // TODO use enum
        key = StringUtils.trimToEmpty(key);
        if (key.equals("headers")) {
            headers = value;
            return;
        }
        if (key.equals("ssl")) {
            if (value.isString()) {
                config.setSslEnabled(true);
                config.setSslAlgorithm(value.getAsString());
            } else {
                config.setSslEnabled(value.isBooleanTrue());
            }
        } else if (key.equals("connectTimeout")) {
            config.setConnectTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("readTimeout")) {
            config.setReadTimeout(Integer.valueOf(value.getAsString()));
        } else if (key.equals("proxy")) {
            if (value.isString()) {
                config.setProxyUri(value.getAsString());
            } else {
                Map<String, Object> map = (Map) value.getAfterConvertingFromJsonOrXmlIfNeeded();
                config.setProxyUri((String) map.get("uri"));
                config.setProxyUsername((String) map.get("username"));
                config.setProxyPassword((String) map.get("password"));
            }
        } else {
            throw new RuntimeException("unexpected 'configure' key: '" + key + "'");
        }
        client.configure(config);
    }
    
    public Map<String, Object> getVariableBindings() {
        Map<String, Object> map = Script.simplify(vars);
        if (readFunction != null) {
            map.put(VAR_READ, readFunction.getValue());
        }        
        return map;
    }

}
