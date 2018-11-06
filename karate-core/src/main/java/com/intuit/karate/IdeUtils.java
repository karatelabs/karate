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
package com.intuit.karate;

import static com.intuit.karate.FileUtils.CLASSPATH_COLON;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Tags;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author pthomas3
 */
public class IdeUtils {

    private static final Pattern COMMAND_NAME = Pattern.compile("--name (.+?\\$)");

    public static void exec(String[] args) {
        String command = System.getProperty("sun.java.command");
        System.out.println("command: " + command);
        boolean isIntellij = command.contains("org.jetbrains");
        RunnerOptions options = RunnerOptions.parseCommandLine(command);
        String name = options.getName();
        List<String> features = options.getFeatures();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<Resource> resources = FileUtils.scanForFeatureFiles(features, cl);
        String tagSelector = Tags.fromCucumberOptionsTags(options.getTags());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            feature.setCallName(name);
            FeatureResult result = Engine.executeFeatureSync(null, feature, tagSelector, null);
            if (isIntellij) {
                log(result);
            }
            Engine.saveResultHtml(Engine.getBuildDir() + File.separator + "surefire-reports", result, null);
        }        
    }

    public static StringUtils.Pair parseCommandLine(String commandLine, String cwd) {
        Matcher matcher = COMMAND_NAME.matcher(commandLine);
        String name;
        if (matcher.find()) {
            name = matcher.group(1);
            commandLine = matcher.replaceFirst("");
        } else {
            name = null;
        }
        List<String> args = Arrays.asList(commandLine.split("\\s+"));
        Iterator<String> iterator = args.iterator();
        String path = null;
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.equals("--plugin") || arg.equals("--glue")) {
                iterator.next();
            }
            if (arg.startsWith("--") || arg.startsWith("com.") || arg.startsWith("cucumber.") || arg.startsWith("org.")) {
                // do nothing
            } else {
                path = arg;
            }
        }
        if (path == null) {
            return null;
        }
        if (cwd == null) {
            cwd = new File("").getAbsoluteFile().getPath();
        }
        cwd = cwd.replace('\\', '/'); // fix for windows
        path = path.substring(cwd.length() + 1);
        if (path.startsWith(FileUtils.SRC_TEST_JAVA)) {
            path = CLASSPATH_COLON + path.substring(FileUtils.SRC_TEST_JAVA.length() + 1);
        } else if (path.startsWith(FileUtils.SRC_TEST_RESOURCES)) {
            path = CLASSPATH_COLON + path.substring(FileUtils.SRC_TEST_RESOURCES.length() + 1);
        }
        return StringUtils.pair(path, name);
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");

    private static final String TEAMCITY_PREFIX = "##teamcity";
    private static final String TEMPLATE_TEST_STARTED = TEAMCITY_PREFIX + "[testStarted timestamp = '%s' locationHint = '%s' captureStandardOutput = 'true' name = '%s']";
    private static final String TEMPLATE_TEST_FAILED = TEAMCITY_PREFIX + "[testFailed timestamp = '%s' details = '%s' message = '%s' name = '%s' %s]";
    private static final String TEMPLATE_SCENARIO_FAILED = TEAMCITY_PREFIX + "[customProgressStatus timestamp='%s' type='testFailed']";
    private static final String TEMPLATE_TEST_PENDING = TEAMCITY_PREFIX + "[testIgnored name = '%s' message = 'Skipped step' timestamp = '%s']";
    private static final String TEMPLATE_TEST_FINISHED = TEAMCITY_PREFIX + "[testFinished timestamp = '%s' duration = '%s' name = '%s']";
    private static final String TEMPLATE_ENTER_THE_MATRIX = TEAMCITY_PREFIX + "[enteredTheMatrix timestamp = '%s']";
    private static final String TEMPLATE_TEST_SUITE_STARTED = TEAMCITY_PREFIX + "[testSuiteStarted timestamp = '%s' locationHint = 'file://%s' name = '%s']";
    private static final String TEMPLATE_TEST_SUITE_FINISHED = TEAMCITY_PREFIX + "[testSuiteFinished timestamp = '%s' name = '%s']";
    private static final String TEMPLATE_SCENARIO_COUNTING_STARTED = TEAMCITY_PREFIX + "[customProgressStatus testsCategory = 'Scenarios' count = '%s' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_COUNTING_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus testsCategory = '' count = '0' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_STARTED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testStarted' timestamp = '%s']";
    private static final String TEMPLATE_SCENARIO_FINISHED = TEAMCITY_PREFIX + "[customProgressStatus type = 'testFinished' timestamp = '%s']";

    private static String escape(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("|", "||").replace("\n", "|n").replace("\r", "|r").replace("'", "|'").replace("[", "|[").replace("]", "|]");
    }

    private static String getCurrentTime() {
        return DATE_FORMAT.format(new Date());
    }

    private static void log(String s) {
        System.out.println(s);
    }

    private static StringUtils.Pair details(Throwable error) {
        String fullMessage = error.getMessage().replace("\r", "").replace("\t", "  ");
        String[] messageInfo = fullMessage.split("\n", 2);
        if (messageInfo.length == 2) {
            return StringUtils.pair(messageInfo[0].trim(), messageInfo[1].trim());
        } else {
            return StringUtils.pair(fullMessage, "");
        }
    }

    private static void log(FeatureResult fr) {
        Feature f = fr.getFeature();
        String uri = fr.getDisplayUri();
        String featureName = Feature.KEYWORD + ": " + escape(f.getName());
        log(String.format(TEMPLATE_ENTER_THE_MATRIX, getCurrentTime()));
        log(String.format(TEMPLATE_SCENARIO_COUNTING_STARTED, 0, getCurrentTime()));
        log(String.format(TEMPLATE_TEST_SUITE_STARTED, getCurrentTime(), uri + ":" + f.getLine(), featureName));
        for (ScenarioResult sr : fr.getScenarioResults()) {
            Scenario s = sr.getScenario();
            String scenarioName = s.getKeyword() + ": " + escape(s.getName());
            log(String.format(TEMPLATE_SCENARIO_STARTED, getCurrentTime()));
            log(String.format(TEMPLATE_TEST_STARTED, getCurrentTime(), uri + ":" + s.getLine(), scenarioName));
            if (sr.isFailed()) {
                StringUtils.Pair error = details(sr.getError());
                log(String.format(TEMPLATE_TEST_FAILED, getCurrentTime(), escape(error.right), escape(error.left), scenarioName, ""));
            }
            log(String.format(TEMPLATE_TEST_FINISHED, getCurrentTime(), sr.getDurationNanos() / 1000000, scenarioName));
        }
        log(String.format(TEMPLATE_TEST_SUITE_FINISHED, getCurrentTime(), featureName));
        log(String.format(TEMPLATE_SCENARIO_COUNTING_FINISHED, getCurrentTime()));
    }

}
