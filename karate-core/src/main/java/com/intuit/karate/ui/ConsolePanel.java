package com.intuit.karate.ui;




import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.core.Table;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.TextAlignment;

/**
 * 
 * @author babusekaran
 *
 */
public class ConsolePanel extends BorderPane{
	
	private final AppSession2 session;
    private final ScenarioPanel2 scenarioPanel;
    private final ScenarioExecutionUnit unit;
	private final TextArea textArea;
	private final Step step;
	private final int index;
	private final String consolePlaceHolder = "Enter your step here for debugging...";
	private final String idle = "●";
	private final String syntaxError = "● Syntax Error";
	private final String passed = "● Passed";
	private final String failed = "● Failed";
	private static String text;
	private static boolean stepModified = false;
	private static boolean stepParseSuccess = false;
	
	public ConsolePanel(AppSession2 session, ScenarioPanel2 scenarioPanel) {
		this.session = session;
        this.unit = scenarioPanel.getScenarioExecutionUnit();
        // Creating a dummy step for console
        this.index = unit.scenario.getIndex()+1;
        this.step = new Step(unit.scenario.getFeature(), unit.scenario, index);
        this.scenarioPanel = scenarioPanel;
        setPadding(App2.PADDING_ALL);
        textArea = new TextArea();
        textArea.setFont(App2.getDefaultFont());
        textArea.setWrapText(true);
        textArea.setMinHeight(0);
        textArea.setPromptText(consolePlaceHolder);
        text = "";
        Label resultLabel = new Label(idle);
        resultLabel.setTextFill(Color.web("#8c8c8c"));
        resultLabel.setFont(new Font(17));
        resultLabel.setAlignment(Pos.CENTER_RIGHT);
        textArea.focusedProperty().addListener((val, before, after) -> {
            if (!after) { // if we lost focus
                String temp = textArea.getText();
                if (!text.equals(temp) && !temp.trim().equals("")) {
                    text = temp;
                    stepParseSuccess = FeatureParser.updateStepFromText(step, text);
                    if(!stepParseSuccess) {
                    	resultLabel.setText(syntaxError);
            			resultLabel.setTextFill(Color.web("#D52B1E"));
            		}
                    else {
                    	resultLabel.setText(idle);
                    	resultLabel.setTextFill(Color.web("#8c8c8c"));
                    	stepModified = true;
                    }
                }
            }
        });
        setCenter(textArea);
        Button runButton = new Button("Run Code");
        runButton.setAlignment(Pos.CENTER_RIGHT);
        runButton.setOnAction(e -> {
        	if(stepModified) {
        		boolean runStatus = false;
        		// if updateStepFromText got a null step due to parse error (Invalid step inbound)
        		if(!stepParseSuccess) { 
        			resultLabel.setText(syntaxError);
        			resultLabel.setTextFill(Color.web("#D52B1E"));
        		}
        		else {
        			if(run().getStatus().equals("passed")) {
        				resultLabel.setText(passed);
        				resultLabel.setTextFill(Color.web("#53B700"));
                	}
        			else {
        				resultLabel.setText(failed);
            			resultLabel.setTextFill(Color.web("#D52B1E"));
        			}
        		}
        	}
        });
        HBox hbox = new HBox(App2.PADDING);
        hbox.getChildren().add(runButton);
        hbox.getChildren().add(resultLabel);
        setBottom(hbox);
        setMargin(runButton, new Insets(2.0, 0, 0, 0));
    }
	
	public Result run() {
		StepResult sr = unit.execute(step);
        unit.result.setStepResult(index, sr);
        scenarioPanel.refreshVars();
        return sr.getResult();
	}
	
}
