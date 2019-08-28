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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Runner;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.StringUtils;
import com.intuit.karate.debug.DapServer;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author pthomas3
 */
public class Main {

    private static final Pattern COMMAND_NAME = Pattern.compile("--name (.+?\\$)");

    public static void main(String[] args) {
        String command;
        if (args.length > 0) {
            command = StringUtils.join(args, ' ');
        } else {
            command = System.getProperty("sun.java.command");
        }
        System.out.println("command: " + command);
        boolean isIntellij = command.contains("org.jetbrains");
        RunnerOptions ro = RunnerOptions.parseCommandLine(command);
        String targetDir = FileUtils.getBuildDir() + File.separator + "surefire-reports";
        int debugPort = ro.getDebugPort();
        if (debugPort != -1) {
            DapServer server = new DapServer(debugPort);
            server.waitSync();
            return;
        }
        CliExecutionHook hook = new CliExecutionHook(true, targetDir, isIntellij);
        Runner.path(ro.getFeatures())
                .tags(ro.getTags()).scenarioName(ro.getName())
                .hook(hook).parallel(ro.getThreads());
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
            path = FileUtils.CLASSPATH_COLON + path.substring(FileUtils.SRC_TEST_JAVA.length() + 1);
        } else if (path.startsWith(FileUtils.SRC_TEST_RESOURCES)) {
            path = FileUtils.CLASSPATH_COLON + path.substring(FileUtils.SRC_TEST_RESOURCES.length() + 1);
        }
        return StringUtils.pair(path, name);
    }

}
