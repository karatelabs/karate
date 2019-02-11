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
package com.intuit.karate.ui;

import com.intuit.karate.CallContext;
import com.intuit.karate.Logger;
import com.intuit.karate.core.ExecutionContext;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.FeatureExecutionUnit;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.ScenarioExecutionUnit;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;

/**
 *
 * @author pthomas3
 */
public class AppSession {

    private final Logger logger = new Logger();
    private final ExecutionContext exec;
    private final FeatureExecutionUnit featureUnit;

    private final BorderPane rootPane;
    private final File workingDir;
    private final FeatureOutlinePanel featureOutlinePanel;
    private final LogPanel logPanel;

    private final List<ScenarioPanel> scenarioPanels;  
    
    private ScenarioExecutionUnit currentlyExecutingScenario;
    
    public AppSession(BorderPane rootPane, File workingDir, File featureFile, String env) {
        this(rootPane, workingDir, FeatureParser.parse(featureFile), env);
    }     
    
    public AppSession(BorderPane rootPane, File workingDir, String featureText, String env) {
        this(rootPane, workingDir, FeatureParser.parseText(null, featureText), env);
    }  
    
    public AppSession(BorderPane rootPane, File workingDir, Feature feature, String envString) {
        this(rootPane, workingDir, feature, envString, new CallContext(null, true));
    }     

    public AppSession(BorderPane rootPane, File workingDir, Feature feature, String env, CallContext callContext) {
        this.rootPane = rootPane;
        this.workingDir = workingDir;
        logPanel = new LogPanel(logger);
        FeatureContext featureContext = FeatureContext.forFeatureAndWorkingDir(env, feature, workingDir);
        exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null);
        featureUnit = new FeatureExecutionUnit(exec);       
        featureUnit.init(logger);
        featureOutlinePanel = new FeatureOutlinePanel(this);
        DragResizer.makeResizable(featureOutlinePanel, false, false, false, true);
        List<ScenarioExecutionUnit> units = featureUnit.getScenarioExecutionUnits();
        scenarioPanels = new ArrayList(units.size());
        units.forEach(unit -> scenarioPanels.add(new ScenarioPanel(this, unit)));
        rootPane.setLeft(featureOutlinePanel);                
        DragResizer.makeResizable(logPanel, false, false, true, false);
        rootPane.setBottom(logPanel);
    }

    public void resetAll() {
    	scenarioPanels.forEach(scenarioPanel -> scenarioPanel.reset());
    }

    public void runAll() {
    	ExecutorService scenarioExecutorService = Executors.newSingleThreadExecutor();
    	Task<Boolean> runAllTask = new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				for (ScenarioPanel scenarioPanel : scenarioPanels) {
					setCurrentlyExecutingScenario(scenarioPanel.getScenarioExecutionUnit());
					scenarioPanel.runAll(scenarioExecutorService);
				}
				return true;
			}
		};
		scenarioExecutorService.submit(runAllTask);
    }

    public BorderPane getRootPane() {
        return rootPane;
    }

    public FeatureOutlinePanel getFeatureOutlinePanel() {
        return featureOutlinePanel;
    }
    
    public List<ScenarioPanel> getScenarioPanels() {
        return scenarioPanels;
    }

    public void setCurrentlyExecutingScenario(ScenarioExecutionUnit unit) {
        this.currentlyExecutingScenario = unit;
    }

    public ScenarioExecutionUnit getCurrentlyExecutingScenario() {
        return currentlyExecutingScenario;
    }        
    
    public void setSelectedScenario(int index) {
        if (index == -1 || scenarioPanels == null || index > scenarioPanels.size() || scenarioPanels.isEmpty()) {
            return;
        }
        rootPane.setCenter(scenarioPanels.get(index));
    }

    public FeatureExecutionUnit getFeatureExecutionUnit() {
        return featureUnit;
    }

    public List<ScenarioExecutionUnit> getScenarioExecutionUnits() {
        return featureUnit.getScenarioExecutionUnits();
    }

    public void logVar(Var var) {
        logPanel.append(var.toString());
    }

    public File getWorkingDir() {
        return workingDir;
    }        

}
