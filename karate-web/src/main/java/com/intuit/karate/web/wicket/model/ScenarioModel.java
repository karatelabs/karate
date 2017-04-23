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
package com.intuit.karate.web.wicket.model;

import com.intuit.karate.cucumber.FeatureSection;
import com.intuit.karate.cucumber.ScenarioWrapper;
import com.intuit.karate.web.service.KarateService;
import com.intuit.karate.web.service.KarateSession;
import org.apache.wicket.injection.Injector;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 *
 * @author pthomas3
 */
public class ScenarioModel extends LoadableDetachableModel<ScenarioWrapper> {
    
    @SpringBean(required = true)
    private KarateService service;    
    
    private final String sessionId;
    private final int sectionIndex;
    private final int scenarioIndex;
    
    public ScenarioModel(KarateSession session, ScenarioWrapper scenario) {
        sessionId = session.getId();
        sectionIndex = scenario.getSection().getIndex();
        scenarioIndex = scenario.getIndex();
        Injector.get().inject(this);
    }

    @Override
    protected ScenarioWrapper load() {
        KarateSession session = service.getSession(sessionId);
        FeatureSection section = session.getFeature().getSections().get(sectionIndex);
        if (section.isOutline()) {
            return section.getScenarioOutline().getScenarios().get(scenarioIndex);
        } else {
            return section.getScenario();
        }
    }
    
}
