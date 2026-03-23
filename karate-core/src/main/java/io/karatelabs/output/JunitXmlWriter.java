/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.output;

import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.gherkin.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Generates JUnit XML reports compatible with CI systems (Jenkins, GitHub Actions, etc.).
 * <p>
 * Output follows the standard JUnit XML schema with per-feature files:
 * <pre>
 * &lt;testsuite name="feature" tests="N" failures="N" time="secs"&gt;
 *   &lt;testcase name="scenario" classname="feature.path" time="secs"&gt;
 *     &lt;properties&gt;
 *       &lt;property name="tag" value="@smoke"/&gt;
 *     &lt;/properties&gt;
 *     &lt;system-out&gt;step logs&lt;/system-out&gt;
 *     &lt;failure message="error"&gt;stacktrace&lt;/failure&gt;
 *   &lt;/testcase&gt;
 * &lt;/testsuite&gt;
 * </pre>
 */
public final class JunitXmlWriter {

    private static final Logger logger = LoggerFactory.getLogger("karate.runtime");

    private JunitXmlWriter() {
    }

    /**
     * Write JUnit XML report for a single feature.
     * <p>
     * Output file is named {@code {packageQualifiedName}.xml}.
     *
     * @param result    the feature result to convert
     * @param outputDir the directory to write the report
     */
    public static void writeFeature(FeatureResult result, Path outputDir) {
        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            String fileName = result.getFeature().getResource().getPackageQualifiedName() + ".xml";
            Path xmlPath = outputDir.resolve(fileName);
            String xml = featureToXml(result);
            Files.writeString(xmlPath, xml);
            logger.debug("JUnit XML written: {}", xmlPath);
        } catch (Exception e) {
            logger.warn("Failed to write JUnit XML for {}: {}", result.getDisplayName(), e.getMessage());
        }
    }

    /**
     * Convert a single feature result to JUnit XML string.
     *
     * @param result the feature result
     * @return XML string
     */
    public static String featureToXml(FeatureResult result) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");

        String packageQualifiedName = result.getFeature().getResource().getPackageQualifiedName();

        StringBuilder xml = new StringBuilder();

        // Root testsuite element (single feature)
        // Use feature name (Feature: title) for testsuite name, not file path
        String featureName = result.getFeature().getName();
        if (featureName == null || featureName.isEmpty()) {
            featureName = result.getDisplayName();
        }
        xml.append("<testsuite");
        xml.append(" name=\"").append(escape(featureName)).append("\"");
        xml.append(" tests=\"").append(result.getScenarioCount()).append("\"");
        xml.append(" failures=\"").append(result.getFailedCount()).append("\"");
        xml.append(" time=\"").append(formatter.format(result.getDurationMillis() / 1000.0)).append("\"");
        xml.append(" skipped=\"0\"");
        xml.append(">");

        // Each scenario becomes a testcase
        for (ScenarioResult sr : result.getScenarioResults()) {
            writeTestcase(xml, sr, packageQualifiedName, formatter);
        }

        xml.append("</testsuite>");
        return xml.toString();
    }

    private static void writeTestcase(StringBuilder xml, ScenarioResult sr, String classname, DecimalFormat formatter) {
        String scenarioName = sr.getScenario().getName();
        if (scenarioName == null || scenarioName.isEmpty()) {
            scenarioName = sr.getScenario().getRefId();
        }

        xml.append("<testcase");
        xml.append(" classname=\"").append(escape(classname)).append("\"");
        xml.append(" name=\"").append(escape(scenarioName)).append("\"");
        xml.append(" time=\"").append(formatter.format(sr.getDurationMillis() / 1000.0)).append("\"");
        xml.append(">");

        // Properties for tags
        List<Tag> tags = sr.getScenario().getTagsEffective();
        if (tags != null && !tags.isEmpty()) {
            xml.append("<properties>");
            for (Tag tag : tags) {
                xml.append("<property name=\"tag\" value=\"").append(escape(tag.toString())).append("\"/>");
            }
            xml.append("</properties>");
        }

        // Step logs in system-out
        StringBuilder stepLogs = new StringBuilder();
        appendStepLogs(sr.getStepResults(), stepLogs);
        if (stepLogs.length() > 0) {
            if (sr.isFailed()) {
                xml.append("<failure message=\"").append(escape(truncate(sr.getFailureMessage(), 1000))).append("\">");
                xml.append(escape(stepLogs.toString()));
                xml.append("</failure>");
            } else {
                xml.append("<system-out>");
                xml.append(escape(stepLogs.toString()));
                xml.append("</system-out>");
            }
        } else if (sr.isFailed()) {
            writeFailure(xml, sr);
        }

        xml.append("</testcase>\n");
    }

    private static void appendStepLogs(List<StepResult> stepResults, StringBuilder sb) {
        if (stepResults == null) {
            return;
        }
        for (StepResult sr : stepResults) {
            String prefix = sr.getStep().getPrefix();
            String text = sr.getStep().getText();
            sb.append(prefix).append(" ").append(text).append("\n");

            // Include step log if present
            String stepLog = sr.getLog();
            if (stepLog != null && !stepLog.isEmpty()) {
                sb.append(stepLog);
                if (!stepLog.endsWith("\n")) {
                    sb.append("\n");
                }
            }

            // Recurse into nested call results
            List<FeatureResult> callResults = sr.getCallResults();
            if (callResults != null) {
                for (FeatureResult fr : callResults) {
                    for (ScenarioResult nestedSr : fr.getScenarioResults()) {
                        appendStepLogs(nestedSr.getStepResults(), sb);
                    }
                }
            }
        }
    }

    private static void writeFailure(StringBuilder xml, ScenarioResult sr) {
        String message = sr.getFailureMessage();
        if (message == null) {
            message = "Unknown failure";
        }

        Throwable error = sr.getError();

        xml.append("<failure message=\"").append(escape(truncate(message, 1000))).append("\">");

        // Include stacktrace if available
        if (error != null) {
            xml.append(escape(getStackTrace(error)));
        } else {
            xml.append(escape(message));
        }

        xml.append("</failure>");
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

}
