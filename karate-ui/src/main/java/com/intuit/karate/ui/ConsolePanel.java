package com.intuit.karate.ui;

import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.Result;
import com.intuit.karate.core.ScenarioExecutionUnit;
import com.intuit.karate.core.Step;
import com.intuit.karate.core.StepResult;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/**
 * 
 * @author babusekaran
 *
 */
public class ConsolePanel extends BorderPane {

	private final AppSession session;
	private final ScenarioPanel scenarioPanel;
	private final ScenarioExecutionUnit unit;
	private final TextArea textArea;
	private final Label resultLabel;
	private final Step step;
	private final int index;
	private final String consolePlaceHolder = "Enter your step here for debugging...";
	private final String idle = "●";
	private final String syntaxError = "● syntax error";
	private final String passed = "● passed";
	private final String failed = "● failed";
	private String text;
	private boolean stepModified = false;
	private boolean stepParseSuccess = false;

	public ConsolePanel(AppSession session, ScenarioPanel scenarioPanel) {
		this.session = session;
		this.unit = scenarioPanel.getScenarioExecutionUnit();
		// Creating a dummy step for console
		this.index = unit.scenario.getIndex() + 1;
		this.step = new Step(unit.scenario.getFeature(), unit.scenario, index);
		this.scenarioPanel = scenarioPanel;
		setPadding(App.PADDING_ALL);
		Label consoleLabel = new Label("Console");
		consoleLabel.setStyle("-fx-font-weight: bold");
		consoleLabel.setPadding(new Insets(0, 0, 3.0, 3.0));
		setTop(consoleLabel);
		setPadding(App.PADDING_ALL);
		textArea = new TextArea();
		textArea.setFont(App.getDefaultFont());
		textArea.setWrapText(true);
		textArea.setMinHeight(0);
		textArea.setPromptText(consolePlaceHolder);
		text = "";
		resultLabel = new Label(idle);
		resultLabel.setTextFill(Color.web("#8c8c8c"));
		resultLabel.setPadding(new Insets(3.0, 0, 0, 0));
		resultLabel.setFont(new Font(15));
		textArea.focusedProperty().addListener((val, before, after) -> {
			if (!after) { // if we lost focus
				String temp = textArea.getText();
				if (!text.equals(temp) && !temp.trim().equals("")) {
					text = temp;
					stepParseSuccess = FeatureParser.updateStepFromText(step, text);
					if (!stepParseSuccess) {
						resultLabel.setText(syntaxError);
						resultLabel.setTextFill(Color.web("#D52B1E"));
					} else {
						resultLabel.setText(idle);
						resultLabel.setTextFill(Color.web("#8c8c8c"));
						stepModified = true;
					}
				}
			}
		});
		setCenter(textArea);
		Button runButton = new Button("Run Code");
		runButton.setOnAction(e -> {
			if (stepModified) {
				if (!stepParseSuccess) {
					resultLabel.setText(syntaxError);
					resultLabel.setTextFill(Color.web("#D52B1E"));
				} else {
					if (run().getStatus().equals("passed")) {
						resultLabel.setText(passed);
						resultLabel.setTextFill(Color.web("#53B700"));
					} else {
						resultLabel.setText(failed);
						resultLabel.setTextFill(Color.web("#D52B1E"));
					}
				}
			}
		});
		HBox hbox = new HBox(App.PADDING);
		hbox.setSpacing(5);
		hbox.getChildren().addAll(runButton, resultLabel);
		hbox.setMargin(runButton, new Insets(1.0, 0, 0, 0));
		hbox.setMargin(resultLabel, new Insets(1.0, 0, 0, 0));
		setBottom(hbox);
		setMargin(hbox, App.PADDING_TOP);
	}

	public Result run() {
		StepResult sr = unit.execute(step);
		unit.result.setStepResult(index, sr);
		scenarioPanel.refreshVars();
		return sr.getResult();
	}
	
	public void refresh() {
		textArea.clear();
		text = "";
		resultLabel.setText(idle);
		resultLabel.setTextFill(Color.web("#8c8c8c"));
	}

}
