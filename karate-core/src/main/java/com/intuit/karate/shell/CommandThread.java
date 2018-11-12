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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

public class CommandThread extends Thread {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CommandThread.class);

    private final File workingDir;
    private final String uniqueName;
    private final Logger logger;
    private final String[] args;
    private final List argList;
    private Process process;
    private final boolean sharedAppender;
    private final LogAppender appender;
    private int exitCode = -1;    

    public CommandThread(String... args) {
        this(null, null, null, null, args);
    }

    public CommandThread(File workingDir, String... args) {
        this(null, null, null, workingDir, args);
    }

    public CommandThread(Logger logger, String uniqueName, String logFile, File workingDir, String... args) {
        setDaemon(true);
        this.uniqueName = uniqueName == null ? System.currentTimeMillis() + "" : uniqueName;
        setName(this.uniqueName);
        this.logger = logger == null ? new Logger() : logger;
        this.workingDir = workingDir == null ? new File(".") : workingDir;
        this.args = args;
        this.workingDir.mkdirs();
        argList = Arrays.asList(args);
        if (logFile == null) {
            appender = LogAppender.NO_OP;
            sharedAppender = false;
        } else { // don't create new file if re-using an existing appender
            LogAppender temp = this.logger.getLogAppender();
            sharedAppender = temp != null;
            appender = sharedAppender ? temp : new FileLogAppender(logFile, this.logger);
        }
    }

    public Logger getLogger() {
        return logger;
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

    public void close() {
        LOGGER.debug("closing command: {}", uniqueName);
        process.destroyForcibly();
    }

    public void run() {
        try {
            logger.debug("command: {}", argList);
            ProcessBuilder pb = new ProcessBuilder(args);
            Map<String, String> env = pb.environment();
            String path = env.get("PATH");
            logger.debug("env PATH: {}", path);
            env.clear();
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            process = pb.start();            
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                appender.append(line);
                logger.debug("{}", line);
            }
            exitCode = process.waitFor();
            if (!sharedAppender) {
                appender.close();
            }
            LOGGER.debug("command complete, exit code: {} - {}", exitCode, argList);
        } catch (Exception e) {
            LOGGER.error("command error: {} - {}", argList, e.getMessage());
        }
    }

}
