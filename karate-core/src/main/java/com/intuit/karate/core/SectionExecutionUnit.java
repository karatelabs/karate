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
import com.intuit.karate.Logger;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import com.intuit.karate.exception.KarateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class SectionExecutionUnit implements ExecutionUnit<FeatureResult> {

    private final StepDefs stepDefs;
    private final FeatureResult featureResult;
    private final LogAppender appender;

    private final boolean outline;
    private final Iterator<Scenario> iterator;

    private int index = 0;
    private List<String> errors;

    public SectionExecutionUnit(FeatureSection section, StepDefs stepDefs, FeatureResult featureResult, LogAppender appender) {
        this.stepDefs = stepDefs;
        this.featureResult = featureResult;
        this.appender = appender;
        if (section.isOutline()) {
            outline = true;
            iterator = section.getScenarioOutline().getScenarios().iterator();
        } else {
            outline = false;
            iterator = Collections.singletonList(section.getScenario()).iterator();
        }
    }

    @Override
    public void submit(Consumer<Runnable> system, BiConsumer<FeatureResult, KarateException> next) {
        if (iterator.hasNext()) {
            Scenario scenario = iterator.next();
            index++;
            ScriptEnv env = stepDefs.context.getEnv();
            Collection<Tag> tagsEffective = scenario.getTagsEffective();
            if (!Tags.evaluate(env.tagSelector, tagsEffective)) {
                Logger logger = stepDefs.context.logger;
                logger.trace("skipping scenario at line: {} with tags effective: {}", scenario.getLine(), tagsEffective);
                SectionExecutionUnit.this.submit(system, next);
            } else {
                ScenarioExecutionUnit unit = new ScenarioExecutionUnit(scenario, stepDefs, featureResult, appender);
                system.accept(() -> {
                    unit.submit(system, (r, e) -> {
                        if (outline) {
                            if (e != null) {
                                if (errors == null) {
                                    errors = new ArrayList();
                                }
                                errors.add("row " + index + ": " + e.getMessage());
                            }
                            // continue even if one example row failed                            
                            SectionExecutionUnit.this.submit(system, next);
                        } else {
                            if (e != null) {
                                next.accept(null, e);
                            } else {
                                SectionExecutionUnit.this.submit(system, next);
                            }
                        }
                    });
                });
            }
        } else {
            KarateException ke;
            if (errors != null) {
                String message = "scenario outline failed:";
                for (String s : errors) {
                    message = message + "\n------\n" + s;
                }
                ke = new KarateException(message);
            } else {
                ke = null;
            }            
            next.accept(featureResult, ke);
        }
    }

}
