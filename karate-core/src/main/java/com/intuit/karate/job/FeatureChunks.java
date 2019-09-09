/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.job;

import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Scenario;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pthomas3
 */
public class FeatureChunks {

    public final List<Scenario> scenarios;
    
    private final ExecutionContext exec;    
    public final List<ChunkResult> chunks;
    private final Runnable onComplete;
    private final int count;
    
    private int completed;

    public FeatureChunks(ExecutionContext exec, List<Scenario> scenarios, Runnable onComplete) {
        this.exec = exec;
        this.scenarios = scenarios;
        count = scenarios.size();
        chunks = new ArrayList(count);
        this.onComplete = onComplete;
    }
    
    protected int incrementCompleted() {
        return ++completed;
    }

    protected boolean isComplete() {
        return completed == count;
    }
    
    public void onComplete() {
        for (ChunkResult chunk : chunks) {
            exec.result.addResult(chunk.getResult());
        }
        onComplete.run();
    }

}
