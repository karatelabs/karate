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

import com.intuit.karate.Results;
import com.intuit.karate.core.ExecutionHook;
import com.intuit.karate.core.ExecutionHookFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author pthomas3
 */
public class SuiteRuntime {
    
    public final File workingDir = new File("");
    public final ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    public final Results results = Results.startTimer(1);    
    public final Collection<ExecutionHook> executionHooks = new ArrayList();
    public final ExecutionHookFactory hookFactory = null;    
    private boolean hooksResolved;
    
    public Collection<ExecutionHook> resolveHooks() {
        if (hookFactory == null || hooksResolved) {
            return executionHooks;
        }
        hooksResolved = true;
        executionHooks.add(hookFactory.create());
        return executionHooks;
    }    
    
}
