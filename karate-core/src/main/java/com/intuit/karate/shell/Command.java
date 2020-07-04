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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Command extends Thread {

    protected static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Command.class);

    private final boolean useLineFeed;
    private final File workingDir;
    private final String uniqueName;
    private final Logger logger;
    private final String[] args;
    private final List argList; // just for logging
    private final boolean sharedAppender;
    private final LogAppender appender;

    private Map<String, String> environment;
    private Consumer<String> listener;
    private Consumer<String> errorListener;
    private boolean redirectErrorStream = true;
    private Console sysOut;
    private Console sysErr;
    private Process process;
    private int exitCode = -1;
    private Exception failureReason;

    public boolean isFailed() {
        return failureReason != null;
    }

    public Exception getFailureReason() {
        return failureReason;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    public void setErrorListener(Consumer<String> errorListener) {
        this.errorListener = errorListener;
    }

    public void setRedirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
    }

    public String getSysOut() {
        return sysOut == null ? null : sysOut.getBuffer();
    }

    public String getSysErr() {        
        return sysErr == null ? null : sysErr.getBuffer();
    }

    public static String exec(boolean useLineFeed, File workingDir, String... args) {
        Command command = new Command(useLineFeed, workingDir, args);
        command.start();
        command.waitSync();
        return command.getSysOut();
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

    private static void sleep(int millis) {
        try {
            LOGGER.trace("sleeping for millis: {}", millis);
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int SLEEP_TIME = 2000;
    private static final int POLL_ATTEMPTS_MAX = 30;

    public static boolean waitForPort(String host, int port) {
        int attempts = 0;
        do {
            SocketAddress address = new InetSocketAddress(host, port);
            try {
                LOGGER.debug("poll attempt #{} for port to be ready - {}:{}", attempts, host, port);
                SocketChannel sock = SocketChannel.open(address);
                sock.close();
                return true;
            } catch (IOException e) {
                sleep(SLEEP_TIME);
            }
        } while (attempts++ < POLL_ATTEMPTS_MAX);
        return false;
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
                sleep(SLEEP_TIME);
            }
        } while (attempts++ < POLL_ATTEMPTS_MAX);
        return false;
    }

    public static boolean waitForSocket(int port) {
        StopListenerThread waiter = new StopListenerThread(port, () -> {
            LOGGER.info("*** exited socket wait succesfully");
        });
        waiter.start();
        port = waiter.getPort();
        System.out.println("*** waiting for socket, type the command below:\ncurl http://localhost:"
                + port + "\nin a new terminal (or open the URL in a web-browser) to proceed ...");
        try {
            waiter.join();
            return true;
        } catch (Exception e) {
            LOGGER.warn("*** wait thread failed: {}", e.getMessage());
            return false;
        }
    }

    public Command(String... args) {
        this(false, null, null, null, null, args);
    }

    public Command(boolean useLineFeed, File workingDir, String... args) {
        this(useLineFeed, null, null, null, workingDir, args);
    }

    public Command(boolean useLineFeed, Logger logger, String uniqueName, String logFile, File workingDir, String... args) {
        this.useLineFeed = useLineFeed;
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
            pb.redirectErrorStream(redirectErrorStream);
            process = pb.start();
            sysOut = new Console(uniqueName + "-out", useLineFeed, process.getInputStream(), logger, appender, listener);
            sysOut.start();
            sysErr = new Console(uniqueName + "-err", useLineFeed, process.getErrorStream(), logger, appender, errorListener);
            sysErr.start();
            exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.debug("command complete, exit code: {} - {}", exitCode, argList);
            } else {
                LOGGER.warn("exit code was non-zero: {} - {}", exitCode, argList);
            }
            // the consoles actually can take more time to flush even after the process has exited
            sysErr.join();
            sysOut.join();
            LOGGER.debug("console readers complete");
            if (!sharedAppender) {
                appender.close();
            }
        } catch (Exception e) {
            failureReason = e;
            LOGGER.error("command error: {} - {}", argList, e.getMessage());
        }
    }

}
