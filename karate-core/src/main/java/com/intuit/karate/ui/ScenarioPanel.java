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

import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 *
 * @author pthomas3
 */
public class ScenarioPanel extends BorderPane {

    private final AppSession session;
    private final ScenarioExecutionUnit unit;
    private final VBox content;
    private final VarsPanel varsPanel;
    private final ConsolePanel consolePanel;

    private final List<StepPanel> stepPanels;
    private StepPanel lastStep;

    public ScenarioExecutionUnit getScenarioExecutionUnit() {
        return unit;
    }

    private final ScenarioContext initialContext;
    private int index;

    public ScenarioPanel(AppSession session, ScenarioExecutionUnit unit) {
        this.session = session;
        this.unit = unit;
        unit.init();
        initialContext = unit.getActions().context.copy();
        content = new VBox(App.PADDING);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        setCenter(scrollPane);
        VBox header = new VBox(App.PADDING);
        header.setPadding(App.PADDING_VER);
        setTop(header);
        String headerText = "Scenario: " + unit.scenario.getDisplayMeta() + " " + unit.scenario.getName();
        Label headerLabel = new Label(headerText);
        header.getChildren().add(headerLabel);
        HBox hbox = new HBox(App.PADDING);
        header.getChildren().add(hbox);
        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> reset());
        Button runAllButton = new Button("Run All Steps");
        runAllButton.setOnAction(e -> Platform.runLater(() -> runAll()));
        hbox.getChildren().add(resetButton);
        hbox.getChildren().add(runAllButton);
        stepPanels = new ArrayList();
        unit.getSteps().forEach(step -> addStepPanel(step));
        lastStep.setLast(true);
        VBox vbox = new VBox(App.PADDING);
        varsPanel = new VarsPanel(session, this);
        vbox.getChildren().add(varsPanel);
        consolePanel = new ConsolePanel(session, this);        
        vbox.getChildren().add(consolePanel);        
        setRight(vbox);
        DragResizer.makeResizable(vbox, false, false, false, true);
        DragResizer.makeResizable(consolePanel, false, false, true, false);        
        reset(); // clear any background results if dynamic scenario
    }

    private void addStepPanel(Step step) {
        lastStep = new StepPanel(session, this, step, index++);
        content.getChildren().add(lastStep);
        stepPanels.add(lastStep);
    }

    public void refreshVars() {
        varsPanel.refresh();
    }
    
    public void refreshConsole() {
		consolePanel.refresh();
	}

    public void runAll() {
        reset();
        ExecutorService scenarioExecutorService = Executors.newSingleThreadExecutor();
        Task<Boolean> runAllTask = new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				stepPanels.forEach(step -> {step.disableRun();});
				for (StepPanel step : stepPanels) {
					if (step.run(true)) {
		                break;
		            }
				}
				unit.setExecuted(true);
				return true;
			}
		};
		runAllTask.setOnSucceeded(onSuccess -> {
			Platform.runLater(() -> {
				session.getFeatureOutlinePanel().refresh();
	        });
		});
		scenarioExecutorService.submit(runAllTask);
    }
    
    public void runAll(ExecutorService stepExecutorService) {
        reset();
        Task<Boolean> runAllTask = new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				stepPanels.forEach(step -> {step.disableRun();});
				for (StepPanel step : stepPanels) {
					if (step.run(true)) {
		                break;
		            }
				}
				unit.setExecuted(true);
				return true;
			}
		};
		runAllTask.setOnSucceeded(onSuccess -> {
			Platform.runLater(() -> {
				session.getFeatureOutlinePanel().refresh();
	        });
		});
		stepExecutorService.submit(runAllTask);
    }
    
    public void runUpto(int index) {
    	ExecutorService scenarioExecutorService = Executors.newSingleThreadExecutor();
    	Task<Boolean> runUptoTask = new Task<Boolean>() {
			@Override
			protected Boolean call() throws Exception {
				stepPanels.forEach(step -> {step.disableRun();});
				for (StepPanel stepPanel : stepPanels) {
		            int stepIndex = stepPanel.getIndex();
		            StepResult sr = unit.result.getStepResult(stepPanel.getIndex());
		            //StepResult sr = stepPanel.getScenarioExecutionUnit().result.getStepResult(stepPanel.getIndex());
		            if (sr != null) {
		                continue;
		            }
		            if (stepPanel.run(true) || stepIndex == index) {
		                break;
		            }
		        }
				stepPanels.forEach(step -> {step.enableRun();});
				return true;
			}
		};
		runUptoTask.setOnSucceeded(onSuccess -> {
			Platform.runLater(() -> {
				session.getFeatureOutlinePanel().refresh();
	        });
		});
		scenarioExecutorService.submit(runUptoTask); 
    }

    public void reset() {
        unit.reset(initialContext.copy());
        refreshVars();
        refreshConsole();
        for (StepPanel stepPanel : stepPanels) {
            stepPanel.initStyles();
        }
        session.getFeatureOutlinePanel().refresh();
    }

}
