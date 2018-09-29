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
package com.intuit.karate.shell;

import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

public class CommandThread extends Thread {

    private final File workingDir;
    private final String uniqueName;
    private final Logger logger;
    private final String[] args;
    private Process process;
    private final LogAppender appender;
    private int exitCode = -1;

    public CommandThread(String... args) {
        this(null, null, null, args);
    }

    public CommandThread(File workingDir, String... args) {
        this(null, null, workingDir, args);
    }

    public CommandThread(Class logClass, String logFile, File workingDir, String... args) {
        setDaemon(true);
        this.uniqueName = System.currentTimeMillis() + "";
        setName("command-" + uniqueName);
        logger = logClass == null ? new Logger() : new Logger(logClass);
        this.workingDir = workingDir == null ? new File(".") : workingDir;
        this.args = args;
        if (logFile == null) {
            appender = LogAppender.NO_OP;
        } else {
            appender = new FileLogAppender(logFile, logger);
        }
    }

    public LogAppender getAppender() {
        return appender;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public int getExitCode() {
        return exitCode;
    }

    public int waitSync() {
        try {
            join();
            return exitCode;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            Map env = pb.environment();
            env.clear();
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            process = pb.start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                appender.append(line);
                logger.trace("{}", line);
            }
            exitCode = process.waitFor();
        } catch (Exception e) {
            System.err.println("thread stopping: " + e.getMessage());
        } finally {
            appender.close();
            System.out.println("command complete: " + uniqueName);
        }
    }

}
