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

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * @author pthomas3
 */
public class AppSession {

    public final File featureFile;
    private FeatureWrapper feature; // mutable, can be re-built
    public final KarateBackend backend;
    public final HeaderPanel headerPanel;
    public final FeaturePanel featurePanel;
    public final VarsPanel varsPanel;
    public final LogPanel logPanel;
    public final HttpPanel httpPanel;

    RunService runner;
    BooleanBinding runningNow;
    BooleanProperty notRunning;
    
    public FeatureWrapper getFeature() {
        return feature;
    }

    public AppSession(File featureFile, String envString) {
        this(featureFile, envString, false);
    }

    public ScriptEnv getEnv() {
        return backend.getEnv();
    }
    
    public void resetBackendAndVarsTable(String env) {
        backend.getObjectFactory().reset(env);
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
        FeatureFilePath ffp = FileUtils.parseFeaturePath(featureFile);
        ScriptEnv env = ScriptEnv.forEnvAndFeatureFile(envString, ffp.file, ffp.searchPaths);
        feature = FeatureWrapper.fromFile(ffp.file, env);
        CallContext callContext = new CallContext(null, true);
        backend = CucumberUtils.getBackendWithGlue(feature, callContext);
        if (!test) {
            notRunning = new SimpleBooleanProperty(Boolean.TRUE);
            runningNow = notRunning.not();
            runner = new RunService(this);
            headerPanel = new HeaderPanel(this);
            featurePanel = new FeaturePanel(this);
            varsPanel = new VarsPanel(this, FXCollections.emptyObservableList());
            logPanel = new LogPanel(backend.getEnv().logger);
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
        varsPanel.refresh(stepVarLists);
        httpPanel.refresh(stepVarLists);
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
        featurePanel.action(AppAction.REFRESH);
        headerPanel.initTextContent();
    }

    public void replaceFeature(String text) {
        feature = feature.replaceText(text);
        featurePanel.refresh();
    }

    public VarLists getVars() {
        return new VarLists(backend.getStepDefs());
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
