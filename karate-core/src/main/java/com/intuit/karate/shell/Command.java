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

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.LogAppender;
import com.intuit.karate.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

public class Command extends Thread {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Command.class);

    private final File workingDir;
    private final String uniqueName;
    private final Logger logger;
    private final String[] args;
    private final List argList;
    private final boolean sharedAppender;
    private final LogAppender appender;

    private Map<String, String> environment;
    private Process process;
    private int exitCode = -1;

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public static String exec(boolean useLineFeed, File workingDir, String... args) {
        Command command = new Command(useLineFeed, workingDir, args);        
        command.start();
        command.waitSync();
        return command.appender.collect();
    }

    public static String[] tokenize(String command) {
        StringTokenizer st = new StringTokenizer(command);
        String[] args = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            args[i] = st.nextToken();
        }
        return args;
    }

    public static String execLine(File workingDir, String command) {
        return exec(false, workingDir, tokenize(command));
    }

    public static String getBuildDir() {
        return FileUtils.getBuildDir();
    }

    private static final Set<Integer> PORTS_IN_USE = ConcurrentHashMap.newKeySet();

    public static synchronized int getFreePort(int preferred) {
        if (preferred != 0 && PORTS_IN_USE.contains(preferred)) {
            LOGGER.trace("preferred port {} in use (karate), will attempt to find free port ...", preferred);
            preferred = 0;
        }
        try {
            ServerSocket s = new ServerSocket(preferred);
            int port = s.getLocalPort();
            LOGGER.debug("found / verified free local port: {}", port);
            s.close();
            PORTS_IN_USE.add(port);
            return port;
        } catch (Exception e) {
            if (preferred > 0) {
                LOGGER.trace("preferred port {} in use (system), re-trying ...", preferred);
                PORTS_IN_USE.add(preferred);
                return getFreePort(0);
            }
            LOGGER.error("failed to find free port: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static boolean waitForHttp(String url) {
        int attempts = 0;
        long startTime = System.currentTimeMillis();
        Http http = Http.forUrl(LogAppender.NO_OP, url);
        do {
            if (attempts > 0) {
                LOGGER.debug("attempt #{} waiting for http to be ready at: {}", attempts, url);
            }
            try {
                Http.Response res = http.get();
                int status = res.status();
                if (status == 200) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    LOGGER.debug("ready to accept http connections after {} ms - {}", elapsedTime, url);
                    return true;
                } else {
                    LOGGER.warn("http get returned non-ok status: {} - {}", status, url);
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(2000);
                } catch (Exception ee) {
                    return false;
                }
            }
        } while (attempts++ < 30);
        return false;
    }

    public Command(String... args) {
        this(false, null, null, null, null, args);
    }

    public Command(boolean useLineFeed, File workingDir, String... args) {
        this(useLineFeed, null, null, null, workingDir, args);
    }

    public Command(boolean useLineFeed, Logger logger, String uniqueName, String logFile, File workingDir, String... args) {
        setDaemon(true);
        this.uniqueName = uniqueName == null ? System.currentTimeMillis() + "" : uniqueName;
        setName(this.uniqueName);
        this.logger = logger == null ? new Logger() : logger;
        this.workingDir = workingDir;
        this.args = args;
        if (workingDir != null) {
            workingDir.mkdirs();
        }
        argList = Arrays.asList(args);
        if (logFile == null) {
            appender = new StringLogAppender(useLineFeed);
            sharedAppender = false;
        } else { // don't create new file if re-using an existing appender
            LogAppender temp = this.logger.getAppender();
            sharedAppender = temp != null;
            if (sharedAppender) {
                appender = temp;
            } else {
                appender = new FileLogAppender(new File(logFile));
                this.logger.setAppender(appender);
            }
        }
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public List getArgList() {
        return argList;
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

    public void close(boolean force) {
        LOGGER.debug("closing command: {}", uniqueName);
        if (force) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    @Override
    public void run() {
        try {
            logger.debug("command: {}", argList);
            ProcessBuilder pb = new ProcessBuilder(args);
            if (environment != null) {
                pb.environment().putAll(environment);
                environment = pb.environment();
            }
            logger.trace("env PATH: {}", pb.environment().get("PATH"));
            if (workingDir != null) {
                pb.directory(workingDir);
            }
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
            if (exitCode == 0) {
                LOGGER.debug("command complete, exit code: {} - {}", exitCode, argList);
            } else {
                LOGGER.warn("exit code was non-zero: {} - {}", exitCode, argList);
            }
        } catch (Exception e) {
            LOGGER.error("command error: {} - {}", argList, e.getMessage());
        }
    }

}
