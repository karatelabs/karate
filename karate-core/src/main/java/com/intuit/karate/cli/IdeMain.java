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
package com.intuit.karate.cli;

import com.intuit.karate.Main;
import com.intuit.karate.Runner;
import com.intuit.karate.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intuit.karate.shell.Command;
import picocli.CommandLine;

/**
 *
 * @author pthomas3
 */
public class IdeMain {

    public static void main(String[] args) {
        String command;
        if (args.length > 0) {
            command = StringUtils.join(args, ' ');
        } else {
            command = System.getProperty("sun.java.command");
        }
        System.out.println("command: " + command);
        Main ro = parseIdeCommandLine(command);
        Runner.Builder rb = Runner.builder();
        if (command.contains("org.jetbrains")) {
            rb.hook(new IntellijHook());
        }
        rb.path(ro.getPaths())
                .tags(ro.getTags())
                .scenarioName(ro.getName())
                .parallel(ro.getThreads());
    }

    private static final Pattern CLI_PLUGIN = Pattern.compile("--plugin\\s+[^\\s]+\\s");
    private static final Pattern CLI_GLUE = Pattern.compile("--glue\\s+[^\\s]+\\s+");
    private static final Pattern CLI_NAME = Pattern.compile("--name \"?([^$\"]+[^ \"]+)\"?");

    public static Main parseStringArgs(String[] args) {
        Main options = CommandLine.populateCommand(new Main(), args);
        List<String> paths = new ArrayList();
        if (options.getPaths() != null) {
            for (String s : options.getPaths()) {
                if (s.startsWith("com.") || s.startsWith("cucumber.") || s.startsWith("org.")) {
                    continue;
                }
                paths.add(s);
            }
            options.setPaths(paths.isEmpty() ? null : paths);
        }
        return options;
    }
    
    private static final String CLI_MAIN = "cucumber.api.cli.Main";

    public static Main parseIdeCommandLine(String line) {
        int pos = line.indexOf(CLI_MAIN);
        if (pos != -1) {
            line = line.substring(pos + CLI_MAIN.length());
        }
        line = line.replace("--monochrome", "");
        Matcher pluginMatcher = CLI_PLUGIN.matcher(line);
        if (pluginMatcher.find()) {
            line = pluginMatcher.replaceFirst("");
        }
        Matcher glueMatcher = CLI_GLUE.matcher(line);
        if (glueMatcher.find()) {
            line = glueMatcher.replaceFirst("");
        }
        Matcher nameMatcher = CLI_NAME.matcher(line);
        String nameTemp;
        if (nameMatcher.find()) {
            nameTemp = nameMatcher.group(1);
            line = nameMatcher.replaceFirst("");
        } else {
            nameTemp = null;
        }
        Main options = Main.parseKarateOptionsAndQuotePath(line);
        options.setName(nameTemp);
        return options;
    }

}
