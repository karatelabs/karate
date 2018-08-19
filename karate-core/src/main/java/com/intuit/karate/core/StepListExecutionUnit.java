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
package com.intuit.karate.core;

import com.intuit.karate.LogAppender;
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.exception.KarateException;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class StepListExecutionUnit implements ExecutionUnit<Boolean> {

    private final Iterator<Step> iterator;
    private final Scenario scenario;
    private final StepDefs stepDefs;
    private final ResultElement collector;
    private final LogAppender appender;
    
    private boolean stopped;
    private KarateException error;

    public StepListExecutionUnit(List<Step> list, Scenario scenario, StepDefs stepDefs, ResultElement collector, LogAppender appender, boolean stopped) {
        this.iterator = list.iterator();
        this.scenario = scenario;
        this.stepDefs = stepDefs;
        this.collector = collector;
        this.appender = appender;
        this.stopped = stopped;
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<Boolean, KarateException> next) {
        if (iterator.hasNext()) {
            Step step = iterator.next();
            if (stopped) {
                system.accept(() -> {
                    collector.addStepResult(new StepResult(step, Result.skipped()));
                    StepListExecutionUnit.this.submit(system, next);
                });
            } else {
                system.accept(() -> {
                    StepExecutionUnit unit = new StepExecutionUnit(step, scenario, stepDefs);
                    unit.submit(system, (r, e) -> {
                        // log appender collection for each step happens here
                        StepResult stepResult = new StepResult(step, r);
                        if (step.getDocString() == null) {
                            String log = StringUtils.trimToNull(appender.collect());
                            if (log != null) {
                                stepResult.putDocString(log);
                            }
                        }
                        collector.addStepResult(stepResult);
                        if (r.isAborted()) {
                            stopped = true;
                        }
                        if (e != null) { // failed
                            stopped = true;
                            error = e;
                        }
                        StepListExecutionUnit.this.submit(system, next);
                    });
                });
            }
        } else {
            next.accept(stopped, error);
        }
    }

}
