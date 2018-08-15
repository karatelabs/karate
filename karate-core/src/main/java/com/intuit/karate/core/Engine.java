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
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.LogAppender;
import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.core.ResultElement.Type;
import com.intuit.karate.exception.KarateAbortException;
import com.intuit.karate.exception.KarateException;
import cucumber.api.DataTable;
import cucumber.api.java.en.When;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class Engine {

    private static final List<MethodPattern> PATTERNS = new ArrayList();
    
    private static final Consumer<Runnable> SYNC_EXECUTOR = r -> r.run();
    private static final BiConsumer<FeatureResult, KarateException> NO_OP = (r, e) -> {};

    static {
        for (Method method : StepDefs.class.getMethods()) {
            When when = method.getDeclaredAnnotation(When.class);
            if (when != null) {
                MethodPattern pattern = new MethodPattern(method, when);
                PATTERNS.add(pattern);
            }
        }
    }

    private Engine() {
        // only static methods
    }

    public static FeatureResult executeSync(Feature feature, StepDefs stepDefs, LogAppender appender) {
        FeatureExecutionUnit unit = new FeatureExecutionUnit(feature, stepDefs, appender);
        unit.submit(SYNC_EXECUTOR, NO_OP);
        return unit.getFeatureResult();
    }

    public static Result execute(Step step, StepDefs stepDefs) {
        String text = step.getText();
        List<MethodMatch> matches = findMethodsMatching(text);
        if (matches.isEmpty()) {
            return Result.failed(0, new KarateException("no step-definition method match found for: " + text));
        } else if (matches.size() > 1) {
            return Result.failed(0, new KarateException("more than one step-definition method matched: " + text + " - " + matches));
        }
        MethodMatch match = matches.get(0);
        Object last;
        if (step.getDocString() != null) {
            last = step.getDocString();
        } else if (step.getTable() != null) {
            last = DataTable.create(step.getTable().getRows());
        } else {
            last = null;
        }
        Object[] args = match.convertArgs(last);
        long startTime = System.nanoTime();
        try {
            match.method.invoke(stepDefs, args);
            return Result.passed(getElapsedTime(startTime));
        } catch (KarateAbortException ke) {
            return Result.aborted(getElapsedTime(startTime));
        } catch (InvocationTargetException e) { // target will be KarateException
            return Result.failed(getElapsedTime(startTime), e.getTargetException());
        } catch (Exception e) {
            return Result.failed(getElapsedTime(startTime), e);
        }
    }
    
    private static String getBaseName(FeatureResult result) {
        return FileUtils.toPackageQualifiedName(result.getUri());
    }

    public static void saveResultJson(String targetDir, FeatureResult result) {
        List<FeatureResult> single = Collections.singletonList(result);
        String json = JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(single));
        FileUtils.writeToFile(new File(targetDir + "/" + getBaseName(result) + ".json"), json);
    }
    
    public static void saveResultXml(String targetDir, FeatureResult result) {
        Document doc = XmlUtils.newDocument();
        Element root = doc.createElement("testsuite");
        doc.appendChild(root);
        root.setAttribute("name", result.getName()); // will be uri
        root.setAttribute("skipped", "0");
        String baseName = getBaseName(result);
        int testCount = 0;
        int failureCount = 0;
        for (ResultElement re : result.getElements()) {
            if (re.getType() == Type.SCENARIO) {
                testCount++;
                if (re.isFailed()) {
                    failureCount++;
                }                
                Element testCase = doc.createElement("testcase");
                root.appendChild(testCase);
                testCase.setAttribute("classname", baseName);
                String name = re.getName();
                if (StringUtils.isBlank(name)) {
                    name = testCount + "";
                }
                testCase.setAttribute("name", name);                
            }
        }        
        root.setAttribute("tests", testCount + "");
        root.setAttribute("failures", failureCount + "");
        root.setAttribute("time", "0");
        String xml = XmlUtils.toString(doc, true);
        FileUtils.writeToFile(new File(targetDir + "/" + getBaseName(result) + ".xml"), xml);
    }

    private static long getElapsedTime(long startTime) {
        return System.nanoTime() - startTime;
    }

    private static List<MethodMatch> findMethodsMatching(String text) {
        List<MethodMatch> matches = new ArrayList(1);
        for (MethodPattern pattern : PATTERNS) {
            List<String> args = pattern.match(text);
            if (args != null) {
                matches.add(new MethodMatch(pattern.method, args));
            }
        }
        return matches;
    }

}
