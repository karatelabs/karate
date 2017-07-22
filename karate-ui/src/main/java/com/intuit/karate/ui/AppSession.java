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
package com.intuit.karate.ui;

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.ScriptValueMap;
import com.intuit.karate.cucumber.CucumberUtils;
import com.intuit.karate.cucumber.FeatureFilePath;
import com.intuit.karate.cucumber.FeatureSection;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.cucumber.KarateBackend;
import com.intuit.karate.cucumber.ScenarioOutlineWrapper;
import com.intuit.karate.cucumber.ScenarioWrapper;
import com.intuit.karate.cucumber.StepWrapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class AppSession {
    
    private static final Logger logger = LoggerFactory.getLogger(AppSession.class);
    
    public FeatureWrapper feature;
    public final KarateBackend backend;
    private FeaturePanel featurePanel;
    private VarsPanel varsPanel;
    private LogPanel logPanel;

    public void setFeaturePanel(FeaturePanel featurePanel) {
        this.featurePanel = featurePanel;
    }

    public FeaturePanel getFeaturePanel() {
        return featurePanel;
    }       

    public void setVarsPanel(VarsPanel varsPanel) {
        this.varsPanel = varsPanel;
    }       

    public VarsPanel getVarsPanel() {
        return varsPanel;
    }

    public void setLogPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
    }

    public LogPanel getLogPanel() {
        return logPanel;
    }        
    
    public AppSession(FeatureWrapper feature, KarateBackend backend) {
        this.feature = feature;
        this.backend = backend;
    }
    
    public void logVar(Var var) {
        if (logPanel != null) {
            logPanel.append(var.getValue().getAsPrettyString());
        }
    }
    
    public void refreshVarsTable() {
        if (varsPanel != null) {
            varsPanel.refresh();
        }
    }
    
    public FeatureSection refresh(FeatureSection section) {
        return feature.getSection(section.getIndex());
    }
    
    public ScenarioOutlineWrapper refresh(ScenarioOutlineWrapper outline) {
        return feature.getSection(outline.getSection().getIndex()).getScenarioOutline();
    }
    
    public ScenarioWrapper refresh(ScenarioWrapper scenario) {
        return feature.getScenario(scenario.getSection().getIndex(), scenario.getIndex());
    }
    
    public StepWrapper refresh(StepWrapper step) {
        int stepIndex = step.getIndex();
        int scenarioIndex = step.getScenario().getIndex();
        int sectionIndex = step.getScenario().getSection().getIndex();
        return feature.getStep(sectionIndex, scenarioIndex, stepIndex);        
    }
    
    public void replace(StepWrapper step, String text) {
        feature = feature.replaceStep(step, text);
        if (featurePanel != null) {
            featurePanel.refresh();
        }
    }
    
    public ObservableList<Var> getVars() {
        if (backend.getStepDefs() == null) {
            return FXCollections.emptyObservableList();
        }
        ScriptValueMap map = backend.getStepDefs().getContext().getVars();
        List<Var> list = new ArrayList(map.size());
        for (Map.Entry<String, ScriptValue> entry : map.entrySet()) {
            list.add(new Var(entry.getKey(), entry.getValue()));
        }
        return FXCollections.observableList(list);
    } 
    
    public static AppSession init(File file, boolean test) {
        FeatureFilePath ffp = FileUtils.parseFeaturePath(file);
        ScriptEnv env = ScriptEnv.init(null, ffp.file, ffp.searchPaths, logger);
        FeatureWrapper feature = FeatureWrapper.fromFile(ffp.file, env);
        KarateBackend backend = CucumberUtils.getBackendWithGlue(env, null, null, false);
        AppSession session = new AppSession(feature, backend);
        if (!test) {
            session.setFeaturePanel(new FeaturePanel(session));
            session.setVarsPanel(new VarsPanel(session));
            session.setLogPanel(new LogPanel());
        }
        return session;
    }
    
}
