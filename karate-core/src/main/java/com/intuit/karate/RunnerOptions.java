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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 * @author pthomas3
 */
@SuppressWarnings("deprecation")
public class RunnerOptions {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RunnerOptions.class);

    private static final Pattern COMMAND_NAME = Pattern.compile("--name \"?([^$\"]+[^ \"]+)\"?");

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean help;

    @Option(names = {"-t", "--tags"}, description = "tags")
    List<String> tags;

    @Option(names = {"-T", "--threads"}, description = "threads")
    int threads = 1;

    @Option(names = {"-n", "--name"}, description = "name of scenario to run")
    String name;

    @Option(names = {"-e", "--env"}, description = "value of 'karate.env'")
    String env;

    @Option(names = {"-d", "--debug"}, arity = "0..1", defaultValue = "-1", fallbackValue = "0",
            description = "debug mode (optional port else dynamically chosen)")
    int debugPort;

    @Parameters(description = "one or more tests (features) or search-paths to run")
    List<String> features;

    // these 3 are here purely to keep ide-s that send these happy, but will be ignored
    @Option(names = {"-m", "--monochrome"}, description = "monochrome (not supported)")
    boolean monochrome;

    @Option(names = {"-g", "--glue"}, description = "glue (not supported)")
    String glue;

    @Option(names = {"-", "--plugin"}, description = "plugin (not supported)")
    List<String> plugins;

    public List<String> getTags() {
        return tags;
    }

    public String getName() {
        return name;
    }

    public void addFeature(String feature) {
        if (features == null) {
            features = new ArrayList(1);
        }
        features.add(feature);
    }

    public List<String> getFeatures() {
        return features;
    }

    public int getThreads() {
        return threads;
    }

    public int getDebugPort() {
        return debugPort;
    }

    public static RunnerOptions parseStringArgs(String[] args) {
        RunnerOptions options = CommandLine.populateCommand(new RunnerOptions(), args);
        List<String> featuresTemp = new ArrayList();
        if (options.features != null) {
            for (String s : options.features) {
                if (s.startsWith("com.") || s.startsWith("cucumber.") || s.startsWith("org.")) {
                    continue;
                }
                featuresTemp.add(s);
            }
            options.features = featuresTemp.isEmpty() ? null : featuresTemp;
        }
        return options;
    }

    public static RunnerOptions parseCommandLine(String line) {
        Matcher matcher = COMMAND_NAME.matcher(line);
        String nameTemp;
        if (matcher.find()) {
            nameTemp = matcher.group(1);
            line = matcher.replaceFirst("");
        } else {
            nameTemp = null;
        }
        String[] args = line.split("\\s+");
        RunnerOptions options = parseStringArgs(args);
        options.name = nameTemp;
        // a little easier for things like the vs code ide to say "-e smoke" etc
        if (options.env != null) { 
            System.setProperty("karate.env", options.env);
        }
        return options;
    }

    public static RunnerOptions fromAnnotationAndSystemProperties(List<String> features, List<String> tags, Class<?> clazz) {
        KarateOptions ko = clazz == null ? null : clazz.getAnnotation(KarateOptions.class);
        if (ko != null) {
            if (ko.tags().length > 0) {
                tags = Arrays.asList(ko.tags());
            }
            if (ko.features().length > 0) {
                features = Arrays.asList(ko.features());
            }
        }
        if (clazz != null && (features == null || features.isEmpty())) {
            String relative = FileUtils.toRelativeClassPath(clazz);
            features = Collections.singletonList(relative);
        }
        String line = System.getProperty("karate.options");
        line = StringUtils.trimToNull(line);
        RunnerOptions options;
        if (line == null) {
            options = new RunnerOptions();
            options.tags = tags;
            options.features = features;
        } else {
            logger.info("found system property 'karate.options': {}", line);
            options = parseCommandLine(line);
            if (options.tags == null) {
                options.tags = tags;
            }
            if (options.features == null) {
                options.features = features;
            }
        }
        return options;
    }

}
