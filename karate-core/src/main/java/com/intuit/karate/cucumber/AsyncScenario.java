/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.cucumber;

import com.intuit.karate.exception.KarateException;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class AsyncScenario implements AsyncAction<Object> {

    private final ScenarioWrapper scenario;
    private final KarateBackend backend;
    private final Iterator<StepWrapper> iterator;

    public AsyncScenario(ScenarioWrapper scenario, KarateBackend backend) {
        this.scenario = scenario;
        this.backend = backend;
        this.iterator = scenario.getSteps().iterator();
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<Object, KarateException> next) {
        if (iterator.hasNext()) {
            StepWrapper step = iterator.next();
            system.accept(() -> {
                AsyncStep as = new AsyncStep(step, backend);
                as.submit(system, (r, e) -> {
                    if (r != null && r.isAbort()) {
                        next.accept(null, null); // abort: exit early
                    } else if (e != null) {
                        next.accept(null, e); // exit with error
                    } else {
                        AsyncScenario.this.submit(system, next);
                    }
                });
            });
        } else {
            next.accept(null, null);
        }        
    }

}
