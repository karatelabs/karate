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

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScriptBridge;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateFailException;
import com.intuit.karate.exception.KarateFileNotFoundException;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * this class exists as a performance optimization - we init Nashorn only once
 * and set up the Bindings to Karate variables only once per scenario
 *
 * we also avoid re-creating hash-maps as far as possible
 *
 * @author pthomas3
 */
public class ScriptBindings implements Bindings {

    public static final String KARATE = "karate";
    public static final String KARATE_ENV = "karate.env";
    public static final String KARATE_CONFIG_DIR = "karate.config.dir";
    private static final String KARATE_DASH_CONFIG = "karate-config";
    private static final String KARATE_DASH_BASE = "karate-base";
    private static final String DOT_JS = ".js";
    public static final String KARATE_CONFIG_JS = KARATE_DASH_CONFIG + DOT_JS;
    private static final String KARATE_BASE_JS = KARATE_DASH_BASE + DOT_JS;
    public static final String READ = "read";
    public static final String DRIVER = "driver";
    public static final String ROBOT = "robot";
    public static final String KEY = "Key";
    public static final String SUREFIRE_REPORTS = "surefire-reports";

    // netty / test-doubles
    public static final String PATH_MATCHES = "pathMatches";
    public static final String METHOD_IS = "methodIs";
    public static final String TYPE_CONTAINS = "typeContains";
    public static final String ACCEPT_CONTAINS = "acceptContains";
    public static final String HEADER_CONTAINS = "headerContains";
    public static final String PARAM_VALUE = "paramValue";
    public static final String PARAM_EXISTS = "paramExists";
    public static final String PATH_PARAMS = "pathParams";
    public static final String PATH_MATCH_SCORES = "pathMatchScores";
    public static final String METHOD_MATCH = "methodMatch";
    public static final String HEADERS_MATCH_SCORE = "headersMatchScore";
    public static final String QUERY_MATCH_SCORE = "queryMatchScore";
    public static final String BODY_PATH = "bodyPath";
    public static final String SERVER_PORT = "serverPort";

    // all threads will share this ! thread isolation is via Bindings (this class)
    private static final ScriptEngine NASHORN = new ScriptEngineManager(null).getEngineByName("nashorn");

    public final ScriptBridge bridge;
    private final ScriptValueMap vars;
    public final Map<String, Object> adds;

    public ScriptBindings(ScenarioContext context) {
        this.vars = context.vars;
        this.adds = new HashMap(10); // read, karate, self, root, parent, nashorn.global, driver, robot, responseBytes
        bridge = new ScriptBridge(context);
        adds.put(KARATE, bridge);
        adds.put(READ, context.read);
    }

    private static final String READ_INVOKE = "%s('%s%s')";
    private static final String READ_KARATE_CONFIG_DEFAULT = String.format(READ_INVOKE, READ, FileUtils.CLASSPATH_COLON, KARATE_CONFIG_JS);
    public static final String READ_KARATE_CONFIG_BASE = String.format(READ_INVOKE, READ, FileUtils.CLASSPATH_COLON, KARATE_BASE_JS);

    public static final String readKarateConfigForEnv(boolean isForDefault, String configDir, String env) {
        if (isForDefault) {
            if (configDir == null) {
                return READ_KARATE_CONFIG_DEFAULT; // only look for classpath:karate-config.js
            } else { // if the user set a config dir, look for karate-config.js but as a file in that dir
                File configFile = new File(configDir + "/" + KARATE_CONFIG_JS);
                if (configFile.exists()) {
                    return String.format(READ_INVOKE, READ, FileUtils.FILE_COLON, configFile.getPath().replace('\\', '/'));
                } else { // if karate-config.js was not over-ridden
                    // user intent is likely to over-ride env config, see 'else' block for this function
                    return READ_KARATE_CONFIG_DEFAULT; // default to classpath:karate-config.js
                }
            }
        } else {
            if (configDir == null) { // look for classpath:karate-config-<env>.js
                return String.format(READ_INVOKE, READ, FileUtils.CLASSPATH_COLON, KARATE_DASH_CONFIG + "-" + env + DOT_JS);
            } else { // look for file:<karate.config.dir>/karate-config-<env>.js
                File configFile = new File(configDir + "/" + KARATE_DASH_CONFIG + "-" + env + DOT_JS);
                return String.format(READ_INVOKE, READ, FileUtils.FILE_COLON, configFile.getPath().replace('\\', '/'));
            }
        }
    }

    public static ScriptValue evalInNashorn(String exp, ScenarioContext context, ScriptEvalContext evalContext) {
        if (context == null) {
            return eval(exp, null);
        } else {
            return context.bindings.updateBindingsAndEval(exp, evalContext);
        }
    }

    private ScriptValue updateBindingsAndEval(String exp, ScriptEvalContext ec) {
        if (ec == null) {
            adds.remove(Script.VAR_SELF);
            adds.remove(Script.VAR_ROOT);
            adds.remove(Script.VAR_PARENT);
        } else {
            // ec.selfValue will never be null
            adds.put(Script.VAR_SELF, ec.selfValue.getAfterConvertingFromJsonOrXmlIfNeeded());
            adds.put(Script.VAR_ROOT, new ScriptValue(ec.root).getAfterConvertingFromJsonOrXmlIfNeeded());
            adds.put(Script.VAR_PARENT, new ScriptValue(ec.parent).getAfterConvertingFromJsonOrXmlIfNeeded());
        }
        return eval(exp, this);
    }

    public static ScriptValue eval(String exp, Bindings bindings) {
        try {
            Object o = bindings == null ? NASHORN.eval(exp) : NASHORN.eval(exp, bindings);
            return new ScriptValue(o);
        } catch (KarateFailException | KarateAbortException | KarateFileNotFoundException ke) {
            throw ke; // reduce log bloat for common file-not-found situation / handle karate.abort() / karate.fail()
        } catch (Exception e) {
            String message = e.toString();
            message = message + "\nstack trace: " + e.getStackTrace()[0];
            throw new RuntimeException("evaluation (js) failed: " + exp + ", " + message, e);
        }
    }

    public static Bindings createBindings() {
        return NASHORN.createBindings();
    }

    public void putAdditionalVariable(String name, Object value) {
        adds.put(name, value);
    }

    @Override
    public Object get(Object key) {
        ScriptValue sv = vars.get(key);
        if (sv == null) {
            return adds.get(key);
        }
        return sv.getAfterConvertingFromJsonOrXmlIfNeeded();
    }

    @Override
    public Object put(String name, Object value) {
        return adds.put(name, value);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        adds.putAll(toMerge);
    }

    @Override
    public boolean containsKey(Object key) {
        // this has to be implemented correctly ! else nashorn won't return 'undefined'
        return vars.containsKey(key) || adds.containsKey(key);
    }

    @Override
    public int size() {
        return vars.size() + adds.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = new HashSet(vars.keySet());
        keys.addAll(adds.keySet());
        return keys;
    }

    // these are never called by nashorn =======================================
    @Override
    public Collection<Object> values() {
        return entrySet().stream().map(Entry::getValue).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Map<String, Object> temp = new HashMap(size());
        temp.putAll(adds); // duplicates possible ! the vars have priority, they will over-write next
        vars.forEach((k, sv) -> {
            // value should never be null, but unit tests may do this
            Object value = sv == null ? null : sv.getAfterConvertingFromJsonOrXmlIfNeeded();
            temp.put(k, value);
        });
        return temp.entrySet();
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public void clear() {
        // this is wrong, but doesn't matter
        adds.clear();
    }

    @Override
    public Object remove(Object key) {
        // this is wrong, but doesn't matter
        return adds.remove(key);
    }

}
