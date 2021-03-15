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
package com.intuit.karate.core;

import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class ScenarioCall {

    public final ScenarioRuntime parentRuntime;
    public final int depth;
    public final Feature feature;
    public final Variable arg;

    private boolean callonce;

    private boolean sharedScope;
    private boolean karateConfigDisabled;
    private int loopIndex = -1;

    public boolean isNone() {
        return depth == 0;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public void setSharedScope(boolean sharedScope) {
        this.sharedScope = sharedScope;
    }

    public boolean isSharedScope() {
        return sharedScope;
    }

    public boolean isCallonce() {
        return callonce;
    }

    public void setCallonce(boolean callonce) {
        this.callonce = callonce;
    }

    public void setKarateConfigDisabled(boolean karateConfigDisabled) {
        this.karateConfigDisabled = karateConfigDisabled;
    }

    public boolean isKarateConfigDisabled() {
        return karateConfigDisabled;
    }

    public static ScenarioCall none(Map<String, Object> arg) {
        return new ScenarioCall(null, null, arg == null ? null : new Variable(arg));
    }

    public ScenarioCall(ScenarioRuntime parentRuntime, Feature feature, Variable arg) {
        this.parentRuntime = parentRuntime;
        this.feature = feature;
        if (parentRuntime == null) {
            depth = 0;
        } else {
            depth = parentRuntime.caller.depth + 1;
        }
        this.arg = arg;
    }

    public static class Result {

        public final Variable value;
        public final Config config;
        public final Map<String, Variable> vars;

        public Result(Variable value, Config config, Map<String, Variable> vars) {
            this.value = value;
            this.config = config;
            this.vars = vars;
        }

    }

}
