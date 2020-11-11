/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation tests (the "Software"), to deal
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

import com.intuit.karate.cli.CliExecutionHook;
import com.intuit.karate.debug.DapServer;
import com.intuit.karate.exception.KarateException;
import com.intuit.karate.formats.postman.PostmanConverter;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.runtime.MockServer;
import com.intuit.karate.server.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 *
 * @author pthomas3
 */
public class Main implements Callable<Void> {

    private static final String DEFAULT_OUTPUT_DIR = "target";
    private static final String LOGBACK_CONFIG = "logback.configurationFile";

    private static Logger logger;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean help;

    @Option(names = {"-m", "--mock"}, description = "mock server file")
    File mock;

    @Option(names = {"-p", "--port"}, description = "mock server port (required for --mock)")
    Integer port;

    @Option(names = {"-w", "--watch"}, description = "watch (and hot-reload) mock server file for changes")
    boolean watch;

    @Option(names = {"-s", "--ssl"}, description = "use ssl / https, will use '"
            + FeatureServer.DEFAULT_CERT_NAME + "' and '" + FeatureServer.DEFAULT_KEY_NAME
            + "' if they exist in the working directory, or generate them")
    boolean ssl;

    @Option(names = {"-c", "--cert"}, description = "ssl certificate (default: " + FeatureServer.DEFAULT_CERT_NAME + ")")
    File cert;

    @Option(names = {"-k", "--key"}, description = "ssl private key (default: " + FeatureServer.DEFAULT_KEY_NAME + ")")
    File key;

    @Option(names = {"-t", "--tags"}, description = "cucumber tags - e.g. '@smoke,~@ignore'")
    List<String> tags;

    @Option(names = {"-T", "--threads"}, description = "number of threads when running tests")
    int threads = 1;

    @Option(names = {"-o", "--output"}, description = "directory where logs and reports are output (default 'target')")
    String output = DEFAULT_OUTPUT_DIR;

    @Parameters(description = "one or more tests (features) or search-paths to run")
    List<String> tests;

    @Option(names = {"-n", "--name"}, description = "scenario name")
    String name;

    @Option(names = {"-e", "--env"}, description = "value of 'karate.env'")
    String env;

    @Option(names = {"-C", "--clean"}, description = "clean output directory")
    boolean clean;

    @Option(names = {"-d", "--debug"}, arity = "0..1", defaultValue = "-1", fallbackValue = "0",
            description = "debug mode (optional port else dynamically chosen)")
    int debugPort;

    @Option(names = {"-j", "--jobserver"}, description = "job server url")
    String jobServerUrl;

    @Option(names = {"-i", "--import"}, description = "import and convert a file")
    String importFile;

    public static void main(String[] args) {
        boolean isClean = false;
        boolean isOutputArg = false;
        String outputDir = DEFAULT_OUTPUT_DIR;
        // hack to manually extract the output dir arg to redirect karate.log if needed
        for (String s : args) {
            if (isOutputArg) {
                outputDir = s;
                isOutputArg = false;
            }
            if (s.startsWith("-o") || s.startsWith("--output")) {
                int pos = s.indexOf('=');
                if (pos != -1) {
                    outputDir = s.substring(pos + 1);
                } else {
                    isOutputArg = true;
                }
            }
            if (s.startsWith("-C") || s.startsWith("--clean")) {
                isClean = true;
            }
        }
        if (isClean) {
            // ensure karate.log is not held open which will prevent 
            // a graceful delete of "target" especially on windows
            System.setProperty(LOGBACK_CONFIG, "logback-nofile.xml");
        } else {
            System.setProperty("karate.output.dir", outputDir);
            // ensure we init logback before anything else
            String logbackConfig = System.getProperty(LOGBACK_CONFIG);
            if (StringUtils.isBlank(logbackConfig)) {
                System.setProperty(LOGBACK_CONFIG, "logback-netty.xml");
            }
        }
        logger = LoggerFactory.getLogger(Main.class);
        logger.info("Karate version: {}", FileUtils.KARATE_VERSION);
        CommandLine cmd = new CommandLine(new Main());
        int returnCode = cmd.execute(args);
        System.exit(returnCode);
    }

    @Override
    public Void call() throws Exception {
        if (clean) {
            FileUtils.deleteDirectory(new File(output));
            logger.info("deleted directory: {}", output);
        }
        if (jobServerUrl != null) {
            JobExecutor.run(jobServerUrl);
            return null;
        }
        if (debugPort != -1) {
            DapServer server = new DapServer(debugPort);
            server.waitSync();
            return null;
        }
        if (tests != null) {
            if (env != null) {
                System.setProperty(ScriptBindings.KARATE_ENV, env);
            }
            String configDir = System.getProperty(ScriptBindings.KARATE_CONFIG_DIR);
            configDir = StringUtils.trimToNull(configDir);
            if (configDir == null) {
                System.setProperty(ScriptBindings.KARATE_CONFIG_DIR, new File("").getAbsolutePath());
            }
            List<String> fixed = tests.stream().map(f -> new File(f).getAbsolutePath()).collect(Collectors.toList());
            // this avoids mixing json created by other means which will break the cucumber report
            String jsonOutputDir = output + File.separator + ScriptBindings.SUREFIRE_REPORTS;
            CliExecutionHook hook = new CliExecutionHook(false, jsonOutputDir, false);
            Results results = Runner
                    .path(fixed).tags(tags).scenarioName(name)
                    .reportDir(jsonOutputDir).hook(hook).parallel(threads);
            if (results.getFailCount() > 0) {
                Exception ke = new KarateException("there are test failures !");
                StackTraceElement[] newTrace = new StackTraceElement[]{
                    new StackTraceElement(".", ".", ".", -1)
                };
                ke.setStackTrace(newTrace);
                throw ke;
            }
            return null;
        }
        if (importFile != null) {
            new PostmanConverter().convert(importFile, output);
            return null;
        }
        if (clean) {
            return null;
        }
        if (mock == null) {
            CommandLine.usage(this, System.err);
            return null;
        }
        if (port == null) {
            System.err.println("--port required for --mock option");
            CommandLine.usage(this, System.err);
            return null;
        }
        // these files will not be created, unless ssl or ssl proxying happens
        // and then they will be lazy-initialized
        if (cert == null || key == null) {
            cert = new File(SslContextFactory.DEFAULT_CERT_NAME);
            key = new File(SslContextFactory.DEFAULT_KEY_NAME);
        }
        if (env != null) { // some advanced mocks may want karate.env
            System.setProperty(ScriptBindings.KARATE_ENV, env);
        }
        MockServer.Builder builder = MockServer.feature(mock).certFile(cert).keyFile(key).corsEnabled();
        if (ssl) {
            builder.https(port);
        } else {
            builder.http(port);
        }
        MockServer server = builder.build();
        if (watch) {
            // TODO
        }
        server.waitSync();
        return null;
    }
}
