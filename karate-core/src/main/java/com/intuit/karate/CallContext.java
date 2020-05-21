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

import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.ExecutionHookFactory;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.ScenarioContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class CallContext {

    public final Feature feature;
    public final ScenarioContext context;
    public final int callDepth;
    public final Map<String, Object> callArg;
    public final boolean reuseParentContext;
    public final boolean evalKarateConfig;
    public final int loopIndex;
    public final String httpClientClass;
    public final Collection<ExecutionHook> executionHooks = new ArrayList();
    public final ExecutionHookFactory hookFactory;
    public final boolean perfMode;

    public static CallContext forCall(Feature feature, ScenarioContext context, Map<String, Object> callArg,
            int loopIndex, boolean reuseParentConfig) {
        return new CallContext(feature, context, context.callDepth + 1, callArg, loopIndex,
                reuseParentConfig, false, null, context.executionHooks, null, context.perfMode);
    }

    public static CallContext forAsync(Feature feature, Collection<ExecutionHook> hooks, ExecutionHookFactory hookFactory, 
            Map<String, Object> arg, boolean perfMode) {
        return new CallContext(feature, null, 0, arg, -1, false, true, null, hooks, hookFactory, perfMode);
    }

    public boolean isCalled() {
        return callDepth > 0;
    }

    private boolean resolved;

    public Collection<ExecutionHook> resolveHooks() {
        if (hookFactory == null || resolved) {
            return executionHooks;
        }
        resolved = true;
        executionHooks.add(hookFactory.create());
        return executionHooks;
    }

    public CallContext(Map<String, Object> callArg, boolean evalKarateConfig, ExecutionHook... hooks) {
        this(null, null, 0, callArg, -1, false, evalKarateConfig, null, hooks.length == 0 ? null : Arrays.asList(hooks), null, false);
    }

    public CallContext(Feature feature, ScenarioContext context, int callDepth, Map<String, Object> callArg, int loopIndex,
            boolean reuseParentContext, boolean evalKarateConfig, String httpClientClass,
            Collection<ExecutionHook> executionHooks, ExecutionHookFactory hookFactory, boolean perfMode) {
        this.feature = feature;
        this.context = context;
        this.callDepth = callDepth;
        this.callArg = callArg;
        this.loopIndex = loopIndex;
        this.reuseParentContext = reuseParentContext;
        this.evalKarateConfig = evalKarateConfig;
        this.httpClientClass = httpClientClass;
        if (executionHooks != null) {
            this.executionHooks.addAll(executionHooks);
        }
        this.hookFactory = hookFactory;
        this.perfMode = perfMode;
    }

}
