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
import java.util.List;
import javafx.scene.layout.BorderPane;

/**
 *
 * @author pthomas3
 */
public class AppSession2 {

    private final Logger logger = new Logger();
    private final ExecutionContext exec;
    private final FeatureExecutionUnit featureUnit;

    private final BorderPane rootPane = new BorderPane();
    private final FeatureOutlinePanel featureOutlinePanel;
    private final LogPanel logPanel;

    private final List<ScenarioPanel2> scenarioPanels;

    public AppSession2(File featureFile, String envString) {
        Feature feature = FeatureParser.parse(featureFile);
        FeatureContext featureContext = new FeatureContext(envString, feature, null, logger);
        CallContext callContext = new CallContext(null, true);
        exec = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, null, null, null);
        featureUnit = new FeatureExecutionUnit(exec);
        featureUnit.init();
        featureOutlinePanel = new FeatureOutlinePanel(this);
        List<ScenarioExecutionUnit> units = featureUnit.getScenarioExecutionUnits();
        scenarioPanels = new ArrayList(units.size());
        units.forEach(unit -> scenarioPanels.add(new ScenarioPanel2(this, unit)));
        rootPane.setLeft(featureOutlinePanel);
        logPanel = new LogPanel(logger);
        rootPane.setBottom(logPanel);
    }

    public void resetAll() {
    	scenarioPanels.forEach(scenarioPanel -> scenarioPanel.reset());
    }

    public void runAll() {
    	scenarioPanels.forEach(scenarioPanel -> scenarioPanel.runAll());
    }

    public BorderPane getRootPane() {
        return rootPane;
    }

    public FeatureOutlinePanel getFeatureOutlinePanel() {
        return featureOutlinePanel;
    }

    public void setSelectedScenario(int index) {
        if (index == -1 || index > scenarioPanels.size() || scenarioPanels.isEmpty()) {
            return;
        }
        rootPane.setCenter(scenarioPanels.get(index));
    }

    public Logger getLogger() {
        return logger;
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

}
