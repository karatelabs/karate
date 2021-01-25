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
package com.intuit.karate.report;

import com.intuit.karate.FileUtils;
import com.intuit.karate.XmlUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.StepResult;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author pthomas3
 */
public class ReportUtils {

    private ReportUtils() {
        // only static methods
    }

    private static final String[] STATIC_RESOURCES = new String[]{
        "favicon.ico",
        "karate-logo.png",
        "karate-logo.svg",
        "com/intuit/karate/report/bootstrap.min.css",
        "com/intuit/karate/report/bootstrap.min.js",
        "com/intuit/karate/report/jquery.min.js",
        "com/intuit/karate/report/jquery.tablesorter.min.js",
        "com/intuit/karate/report/karate-report.css",
        "com/intuit/karate/report/karate-report.js"
    };

    public static String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");
        return sdf.format(new Date());
    }

    private static void copyToFile(String classPath, String destPath) {
        InputStream is = ResourceUtils.classPathResourceToStream(classPath);
        byte[] bytes = FileUtils.toBytes(is);
        FileUtils.writeToFile(new File(destPath), bytes);
    }

    public static void initStaticResources(String targetDir) {
        String resPath = targetDir + File.separator + "res" + File.separator;
        File resFile = new File(resPath);
        if (resFile.exists()) {
            return;
        }
        for (String path : STATIC_RESOURCES) {
            int pos = path.lastIndexOf('/');
            if (pos == -1) {
                copyToFile(path, resFile.getParent() + File.separator + path);
            } else {
                copyToFile(path, resPath + path.substring(pos + 1));
            }
        }
    }

    private static final double MILLION = 1000000;
    private static final double BILLION = 1000000000;

    public static double nanosToSeconds(long nanos) {
        return (double) nanos / BILLION;
    }

    public static double nanosToMillis(long nanos) {
        return (double) nanos / MILLION;
    }

    public static File saveKarateJson(String targetDir, FeatureResult result, String fileName) {
        if (fileName == null) {
            fileName = result.getFeature().getKarateJsonFileName();
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, JsonUtils.toJson(result.toKarateJson()));
        return file;
    }

    public static File saveCucumberJson(String targetDir, FeatureResult result, String fileName) {
        if (fileName == null) {
            fileName = result.getFeature().getPackageQualifiedName() + ".json";
        }
        File file = new File(targetDir + File.separator + fileName);
        String json = JsonUtils.toJson(Collections.singletonList(result.toCucumberJson()));
        FileUtils.writeToFile(file, json);
        return file;
    }

    private static Throwable appendSteps(List<StepResult> steps, StringBuilder sb) {
        Throwable error = null;
        for (StepResult sr : steps) {
            int length = sb.length();
            sb.append(sr.getStep().getPrefix());
            sb.append(' ');
            sb.append(sr.getStep().getText());
            sb.append(' ');
            do {
                sb.append('.');
            } while (sb.length() - length < 75);
            sb.append(' ');
            sb.append(sr.getResult().getStatus());
            sb.append('\n');
            if (sr.getResult().isFailed()) {
                sb.append("\nStack Trace:\n");
                StringWriter sw = new StringWriter();
                error = sr.getResult().getError();
                error.printStackTrace(new PrintWriter(sw));
                sb.append(sw.toString());
                sb.append('\n');
            }
        }
        return error;
    }

    public static File saveJunitXml(String targetDir, FeatureResult result, String fileName) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        formatter.applyPattern("0.######");
        Document doc = XmlUtils.newDocument();
        Element root = doc.createElement("testsuite");
        doc.appendChild(root);
        root.setAttribute("tests", result.getScenarioCount() + "");
        root.setAttribute("failures", result.getFailedCount() + "");
        root.setAttribute("time", formatter.format(result.getDurationMillis() / 1000));
        root.setAttribute("name", result.getDisplayName()); // will be uri
        root.setAttribute("skipped", "0");
        StringBuilder xmlString = new StringBuilder();
        xmlString.append(XmlUtils.toString(doc, false).replace("/>", ">"));
        String baseName = result.getFeature().getPackageQualifiedName();
        Iterator<ScenarioResult> iterator = result.getScenarioResults().iterator();
        while (iterator.hasNext()) {
            ScenarioResult sr = iterator.next();
            Element testCase = doc.createElement("testcase");
            testCase.setAttribute("classname", baseName);
            StringBuilder sb = new StringBuilder();
            Throwable error = appendSteps(sr.getStepResults(), sb);
            testCase.setAttribute("name", sr.getScenario().getRefIdAndName());
            testCase.setAttribute("time", formatter.format(sr.getDurationMillis() / 1000));
            Element stepsHolder;
            if (error != null) {
                stepsHolder = doc.createElement("failure");
                stepsHolder.setAttribute("message", error.getMessage());
            } else {
                stepsHolder = doc.createElement("system-out");
            }
            testCase.appendChild(stepsHolder);
            stepsHolder.setTextContent(sb.toString());
            xmlString.append(XmlUtils.toString(testCase)).append('\n');
        }
        xmlString.append("</testsuite>");
        if (fileName == null) {
            fileName = baseName + ".xml";
        }
        File file = new File(targetDir + File.separator + fileName);
        FileUtils.writeToFile(file, xmlString.toString());
        return file;
    }

}
