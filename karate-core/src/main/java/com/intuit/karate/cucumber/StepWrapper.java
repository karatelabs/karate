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
package com.intuit.karate.cucumber;

import com.intuit.karate.StringUtils;
import gherkin.formatter.model.DocString;
import gherkin.formatter.model.Step;

/**
 *
 * @author pthomas3
 */
public class StepWrapper {

    private final ScenarioWrapper scenario;
    private final int index;
    private final Step step;
    private final boolean background;
    private final String priorText;
    private final String text;

    public StepWrapper(ScenarioWrapper scenario, int index, String priorText, Step step, boolean background) {
        this.scenario = scenario;
        this.index = index;
        this.priorText = StringUtils.trimToNull(priorText);
        this.background = background;
        this.step = step;
        this.text = getStepText(step, scenario);
    }
    
    private static String getStepText(Step step, ScenarioWrapper scenario) {
        StringBuilder sb = new StringBuilder();
        sb.append(step.getKeyword());
        sb.append(step.getName());
        DocString docString = step.getDocString();
        if (docString != null) {
            sb.append("\n\"\"\"\n");
            sb.append(docString.getValue());
            sb.append("\n\"\"\"");
        }
        if (step.getRows() != null) {
            String text = scenario.getFeature().joinLines(step.getLine(), step.getLineRange().getLast() + 1);
            sb.append('\n').append(text);
        }
        return sb.toString();
    }

    public boolean isHttpCall() {
        String name = step.getName();
        return name.startsWith("method") || name.startsWith("soap");
    }

    public int getIndex() {
        return index;
    }

    public boolean isBackground() {
        return background;
    }
    
    public boolean isPriorTextPresent() {
        return priorText != null;
    }

    public String getPriorText() {
        return priorText;
    }
    
    public int getPriorTextLineCount() {
        if (!isPriorTextPresent()) {
            return 0;
        } 
        String[] split = priorText.split("\n");
        return split.length;
    }

    public Step getStep() {
        return step;
    }

    public int getStartLine() {
        return step.getLine() - 1;
    }

    public int getEndLine() {
        return step.getLineRange().getLast() - 1;
    }

    public ScenarioWrapper getScenario() {
        return scenario;
    }

    public String getText() {
        return text;
    }

    public int getLineCount() {
        return getEndLine() - getStartLine() + 1;
    }

}
