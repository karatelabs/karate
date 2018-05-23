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

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import cucumber.runtime.CucumberException;
import cucumber.runtime.io.URLOutputStream;
import cucumber.runtime.io.UTF8OutputStreamWriter;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * adapted from cucumber.runtime.formatter.JUnitFormatter
 *
 * @author pthomas3
 */
public class KarateJunitFormatter implements Formatter, Reporter {

    private static final Logger logger = LoggerFactory.getLogger(KarateJunitFormatter.class);

    private final Writer out;
    private final Document doc;
    private final Element rootElement;

    private TestCase testCase;
    private Element currentElement;

    private final String featurePath;
    private final String reportPath;

    private int currentScenario;
    private int exampleNumber;

    private int testCount;
    private int failCount;
    private int skipCount;
    private double timeTaken;
    private Set<String> failMessages;    

    public int getTestCount() {
        return testCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public double getTimeTaken() {
        return timeTaken;
    }

    public boolean isFail() {
        return failCount > 0;
    }

    public String getFeaturePath() {
        return featurePath;
    }

    public Collection<String> getFailMessages() {
        return failMessages;
    }        

    public KarateJunitFormatter(String featurePath, String reportPath) throws IOException {
        this.featurePath = featurePath;
        this.reportPath = reportPath;
        logger.trace(">> {}", reportPath);
        URL url = FileUtils.toFileUrl(reportPath);
        this.out = new UTF8OutputStreamWriter(new URLOutputStream(url));
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            rootElement = doc.createElement("testsuite");
            doc.appendChild(rootElement);
        } catch (ParserConfigurationException e) {
            throw new CucumberException("Error while processing unit report", e);
        }
    }

    @Override
    public void feature(Feature feature) {
        testCase = new TestCase();
        testCase.feature = feature;
    }

    @Override
    public void step(Step step) {
        testCase.steps.add(step);
    }

    private void printStatsToConsole() {
        System.out.println("---------------------------------------------------------");
        System.out.println("feature: " + featurePath);
        System.out.println("report: " + reportPath);
        System.out.println(String.format("scenarios: %2d | failed: %2d | skipped: %2d | time: %.2f", testCount, failCount, skipCount, timeTaken));
        System.out.println("---------------------------------------------------------");
    }

    @Override
    public void done() {
        try {
            String featureName = StringUtils.trimToNull(testCase.feature.getName());
            if (featureName == null) {
                featureName = featurePath;
            }
            rootElement.setAttribute("name", featureName);
            String testCountString = rootElement.getAttribute("tests");
            try {
                testCount = Integer.valueOf(testCountString);
            } catch (Exception e) {
                testCount = 0;
                logger.warn("invalid test count: {}", e.getMessage());
            }
            failCount = rootElement.getElementsByTagName("failure").getLength();
            rootElement.setAttribute("failures", String.valueOf(failCount));
            skipCount = rootElement.getElementsByTagName("skipped").getLength();
            rootElement.setAttribute("skipped", String.valueOf(skipCount));
            timeTaken = sumTimes(rootElement.getElementsByTagName("testcase"));
            rootElement.setAttribute("time", formatTime(timeTaken));
            printStatsToConsole();
            if (rootElement.getElementsByTagName("testcase").getLength() == 0) {
                addDummyTestCase(); // to avoid failed Jenkins jobs
            }
            TransformerFactory transfac = TransformerFactory.newInstance();
            Transformer trans = transfac.newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(out);
            DOMSource source = new DOMSource(doc);
            trans.transform(source, result);
        } catch (TransformerException e) {
            throw new CucumberException("Error while transforming.", e);
        }
        logger.trace("<< {}", reportPath);
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        testCase.steps.clear();
        testCase.results.clear();
        currentScenario++;
        testCase.scenario = scenario;
        currentElement = testCase.createTestCaseElement(doc);
        testCase.writeElement(currentElement);
        rootElement.appendChild(currentElement);
        increaseAttributeValue(rootElement, "tests");
    }

    @Override
    public void examples(Examples examples) {
        exampleNumber = 0;
    }        

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        if (testCase.steps.isEmpty()) {
            testCase.handleEmptyTestCase(doc, currentElement);
        }
    }

    private void addDummyTestCase() {
        Element dummy = doc.createElement("testcase");
        dummy.setAttribute("classname", "dummy");
        dummy.setAttribute("name", "dummy");
        rootElement.appendChild(dummy);
        Element skipped = doc.createElement("skipped");
        skipped.setAttribute("message", "No features found");
        dummy.appendChild(skipped);
    }

    @Override
    public void result(Result result) {
        testCase.results.add(result);
        testCase.updateElement(currentElement);
    }

    private double sumTimes(NodeList testCaseNodes) {
        double totalDurationSecondsForAllTimes = 0.0d;
        for (int i = 0; i < testCaseNodes.getLength(); i++) {
            try {
                double testCaseTime
                        = Double.parseDouble(testCaseNodes.item(i).getAttributes().getNamedItem("time").getNodeValue());
                totalDurationSecondsForAllTimes += testCaseTime;
            } catch (NumberFormatException e) {
                throw new CucumberException(e);
            } catch (NullPointerException e) {
                throw new CucumberException(e);
            }
        }
        return totalDurationSecondsForAllTimes;
    }

    private String formatTime(double time) {
        DecimalFormat nfmt = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        nfmt.applyPattern("0.######");
        return nfmt.format(time);
    }

    private void increaseAttributeValue(Element element, String attribute) {
        int value = 0;
        if (element.hasAttribute(attribute)) {
            value = Integer.parseInt(element.getAttribute(attribute));
        }
        element.setAttribute(attribute, String.valueOf(++value));
    }

    private class TestCase {

        private final DecimalFormat NUMBER_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);

        private TestCase(Scenario scenario) {
            this.scenario = scenario;
            NUMBER_FORMAT.applyPattern("0.######");
        }

        private TestCase() {
            this(null);
        }

        Scenario scenario;
        private Feature feature;        
        final List<Step> steps = new ArrayList();
        final List<Result> results = new ArrayList();        

        private Element createTestCaseElement(Document doc) {
            return doc.createElement("testcase");
        }

        private void writeElement(Element tc) {
            tc.setAttribute("classname", featurePath);
            tc.setAttribute("name", calculateElementName(scenario));
        }

        private String calculateElementName(Scenario scenario) {
            String scenarioName = StringUtils.trimToNull(scenario.getName());
            if (scenarioName == null) {
                scenarioName = currentScenario + "";
            }
            if (scenario.getKeyword().equals("Scenario Outline")) {
                return scenarioName + " (" + (++exampleNumber) + ")";
            } else {
                return scenarioName;
            }
        }

        public void updateElement(Element tc) {
            tc.setAttribute("time", calculateTotalDurationString());
            StringBuilder sb = new StringBuilder();
            addStepAndResultListing(sb);
            Result skipped = null, failed = null;
            for (Result result : results) {
                if ("failed".equals(result.getStatus())) {
                    failed = result;
                }
                if ("undefined".equals(result.getStatus()) || "pending".equals(result.getStatus())) {
                    skipped = result;
                }
            }
            Element child;
            if (failed != null) {
                addStackTrace(sb, failed);
                child = createElementWithMessage(doc, sb, "failure", failed.getErrorMessage());
                if (failMessages == null) {
                    // TODO investigate why a set is needed, a list ends up with mysterious duplicates
                    failMessages = new LinkedHashSet();
                }
                failMessages.add(failed.getErrorMessage());
            } else if (skipped != null) {
                child = createElement(doc, sb, "skipped");
            } else {
                child = createElement(doc, sb, "system-out");
            }
            Node existingChild = tc.getFirstChild();
            if (existingChild == null) {
                tc.appendChild(child);
            } else {
                tc.replaceChild(child, existingChild);
            }
        }

        public void handleEmptyTestCase(Document doc, Element tc) {
            tc.setAttribute("time", calculateTotalDurationString());
            Element child = createElementWithMessage(doc, new StringBuilder(), "skipped", "The scenario has no steps");
            tc.appendChild(child);
        }

        private String calculateTotalDurationString() {
            long totalDurationNanos = 0;
            for (Result r : results) {
                totalDurationNanos += r.getDuration() == null ? 0 : r.getDuration();
            }
            double totalDurationSeconds = ((double) totalDurationNanos) / 1000000000;
            return NUMBER_FORMAT.format(totalDurationSeconds);
        }

        private void addStepAndResultListing(StringBuilder sb) {
            for (int i = 0; i < steps.size(); i++) {
                int length = sb.length();
                String resultStatus = "not executed";
                if (i < results.size()) {
                    resultStatus = results.get(i).getStatus();
                }
                sb.append(steps.get(i).getKeyword());
                sb.append(steps.get(i).getName());
                do {
                    sb.append(".");
                } while (sb.length() - length < 76);
                sb.append(resultStatus);
                sb.append("\n");
            }
        }

        private void addStackTrace(StringBuilder sb, Result failed) {
            sb.append("\nStackTrace:\n");
            StringWriter sw = new StringWriter();
            failed.getError().printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }

        private Element createElementWithMessage(Document doc, StringBuilder sb, String elementType, String message) {
            Element child = createElement(doc, sb, elementType);
            child.setAttribute("message", message);
            return child;
        }

        private Element createElement(Document doc, StringBuilder sb, String elementType) {
            Element child = doc.createElement(elementType);
            child.appendChild(doc.createCDATASection(sb.toString()));
            return child;
        }

    }

    //==========================================================================
    
    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {

    }

    @Override
    public void uri(String uri) {

    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {

    }

    @Override
    public void background(Background background) {

    }

    @Override
    public void scenario(Scenario scenario) {

    }

    @Override
    public void close() {

    }

    @Override
    public void eof() {

    }

    @Override
    public void before(Match match, Result result) {

    }

    @Override
    public void after(Match match, Result result) {

    }

    @Override
    public void match(Match match) {

    }

    @Override
    public void embedding(String mimeType, byte[] data) {

    }

    @Override
    public void write(String text) {

    }    

}
