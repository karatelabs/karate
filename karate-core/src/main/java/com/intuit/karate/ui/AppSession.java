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

import com.intuit.karate.CallContext;
import com.intuit.karate.StepActions;
import com.intuit.karate.Logger;
import com.intuit.karate.FeatureContext;
import com.intuit.karate.StepDefs;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureSection;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioOutline;
import com.intuit.karate.core.Step;
import java.io.File;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;

/**
 *
 * @author pthomas3
 */
public class AppSession {

    public final File featureFile;
    private Feature feature; // mutable, can be re-built    
    public final HeaderPanel headerPanel;
    public final FeaturePanel featurePanel;
    public final VarsPanel varsPanel;
    public final LogPanel logPanel;
    public final HttpPanel httpPanel;
    public final Logger logger = new Logger();
    
    private StepActions actions;
    
    RunService runner;
    BooleanBinding runningNow;
    BooleanProperty notRunning;
    
    public Feature getFeature() {
        return feature;
    }

    public AppSession(File featureFile, String envString) {
        this(featureFile, envString, false);
    }

    public StepActions getActions() {
        return actions;
    }    

    public FeatureContext getEnv() {
        return actions.context.getFeatureContext();
    }
    
    public void resetBackendAndVarsTable(String envString) {
        FeatureContext env = new FeatureContext(envString, feature, null, logger);
        actions = new StepActions(env, new CallContext(null, true));
        refreshVarsTable();        
    }

    public void resetAll(String env) {
        resetBackendAndVarsTable(env);
        featurePanel.action(AppAction.RESET);
    }
    
    public void runAll() {
        synchronized (notRunning) {
            notRunning.setValue(false);
            runner.runUptoStep(null);
        }
    }

    public void runUpto(StepPanel stepPanel) {
        synchronized (notRunning) {
            notRunning.setValue(false);
            runner.runUptoStep(stepPanel);
        }
    }

    public AppSession(File featureFile, String envString, boolean test) {
        this.featureFile = featureFile;
        feature = FeatureParser.parse(featureFile);
        resetBackendAndVarsTable(envString);
        if (!test) {
            notRunning = new SimpleBooleanProperty(Boolean.TRUE);
            runningNow = notRunning.not();
            runner = new RunService(this);
            headerPanel = new HeaderPanel(this);
            featurePanel = new FeaturePanel(this);
            varsPanel = new VarsPanel(this, FXCollections.emptyObservableList());
            logPanel = new LogPanel(logger);
            httpPanel = new HttpPanel();
        } else {
            headerPanel = null;
            featurePanel = null;
            varsPanel = null;
            logPanel = null;
            httpPanel = null;
        }
    }

    public void logVar(Var var) {
        if (logPanel != null) {
            logPanel.append(var.toString());
        }
    }

    public void refreshVarsTable() {
        // show session vars (last executed step)
        refreshVarsTable(getVars());
    }

    public void refreshVarsTable(VarLists stepVarLists) {
        if (varsPanel != null) { // just in case called from constructor
            varsPanel.refresh(stepVarLists);
            httpPanel.refresh(stepVarLists);
        }
    }

    public FeatureSection refresh(FeatureSection section) {
        return feature.getSection(section.getIndex());
    }

    public ScenarioOutline refresh(ScenarioOutline outline) {
        return feature.getSection(outline.getSection().getIndex()).getScenarioOutline();
    }

    public Scenario refresh(Scenario scenario) {
        return feature.getScenario(scenario.getSection().getIndex(), scenario.getIndex());
    }

    public Step refresh(Step step) {
        int stepIndex = step.getIndex();
        int scenarioIndex = step.getScenario().getIndex();
        int sectionIndex = step.getScenario().getSection().getIndex();
        return feature.getStep(sectionIndex, scenarioIndex, stepIndex);
    }

    public void replace(Step step, String text) {
        feature = feature.replaceStep(step, text);
        featurePanel.action(AppAction.REFRESH);
        headerPanel.initTextContent();
    }

    public void replaceFeature(String text) {
        feature = feature.replaceText(text);
        featurePanel.refresh();
    }

    public VarLists getVars() {
        return new VarLists(actions == null ? null : actions.context);
    }

    public BooleanBinding isRunningNow() {
        return runningNow;
    }

    public void markRunStopped() {
        synchronized (notRunning) {
            notRunning.setValue(true);
        }
    }

    public void stepIntoFeature(StepPanel stepPanel) {
        logPanel.append("TODO: stepIntoFeature coming soon to karate-ui");
    }
}
