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

import com.intuit.karate.ScriptEnv;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.CucumberTagStatement;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author pthomas3
 */
public class FeatureWrapper {
    
    private final String text;
    private final List<String> lines;
    private final CucumberFeature feature;
    private final List<FeatureSection> featureSections;   
    
    private final ScriptEnv scriptEnv;

    public ScriptEnv getEnv() {
        return scriptEnv;
    }

    public static FeatureWrapper fromFile(File file, ClassLoader classLoader) {        
        try {
            String text = FileUtils.readFileToString(file, "utf-8");
            return new FeatureWrapper(text, ScriptEnv.init(file.getParentFile(), file.getName(), classLoader));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    
    
    public static FeatureWrapper fromFile(File file, ScriptEnv env) {        
        try {
            String text = FileUtils.readFileToString(file, "utf-8");
            return new FeatureWrapper(text, env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    
    
    public static FeatureWrapper fromString(String text, ScriptEnv scriptEnv) {
        return new FeatureWrapper(text, scriptEnv);
    }
        
    public static FeatureWrapper fromStream(InputStream is, ScriptEnv scriptEnv) {
        try {
            String text = IOUtils.toString(is, "utf-8");
            return new FeatureWrapper(text, scriptEnv);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public String joinLines(int startLine, int endLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i < endLine; i++) {
            String line = lines.get(i);
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
    
    public String joinLines() {
        int lineCount = lines.size();
        return joinLines(0, lineCount);
    }
       
    
    public List<String> getLines() {
        return lines;
    }

    public CucumberFeature getFeature() {
        return feature;
    }  

    public List<FeatureSection> getSections() {
        return featureSections;
    }        

    public String getText() {
        return text;
    }
    
    public FeatureWrapper addLine(int index, String line) {
        lines.add(index, line);
        return new FeatureWrapper(joinLines(), scriptEnv);
    }
    
    public FeatureWrapper replaceStep(StepWrapper step, String text) {
        return replaceLines(step.getStartLine(), step.getEndLine(), text);
    }
    
    public FeatureWrapper replaceLines(int start, int end, String text) {
        for (int i = start; i < end; i++) {
            lines.remove(start);
        }
        lines.set(start, text);
        return new FeatureWrapper(joinLines(), scriptEnv);
    }

    public FeatureWrapper removeLine(int index) {
        lines.remove(index);
        return new FeatureWrapper(joinLines(), scriptEnv);
    }
    
    private FeatureWrapper(String text, ScriptEnv scriptEnv) {        
        this.text = text;
        this.scriptEnv = scriptEnv;
        this.feature = CucumberUtils.parse(text);
        try {
            InputStream is = IOUtils.toInputStream(text, "utf-8");
            this.lines = IOUtils.readLines(is, "utf-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }                
        featureSections = new ArrayList<>();
        List<CucumberTagStatement> elements = feature.getFeatureElements();
        int count = elements.size();
        for (int i = 0; i < count; i++) {
            CucumberTagStatement cts = elements.get(i);
            if (cts instanceof CucumberScenario) {
                CucumberScenario sw = (CucumberScenario) cts;
                ScenarioWrapper scenario = new ScenarioWrapper(this, -1, sw, null);
                FeatureSection section = new FeatureSection(i, this, scenario, null);               
                featureSections.add(section);
            } else if (cts instanceof CucumberScenarioOutline) {
                CucumberScenarioOutline cso = (CucumberScenarioOutline) cts;
                ScenarioOutlineWrapper scenarioOutline = new ScenarioOutlineWrapper(this, cso);
                FeatureSection section = new FeatureSection(i, this, null, scenarioOutline);                
                featureSections.add(section);
            } else {
                throw new RuntimeException("unexpected type: " + cts.getClass());
            }
        }
    }    
    
}
