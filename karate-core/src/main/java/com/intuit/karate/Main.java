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

import com.intuit.karate.core.MockServer;
import com.intuit.karate.core.RuntimeHookFactory;
import com.intuit.karate.debug.DapServer;
import com.intuit.karate.formats.PostmanConverter;
import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.RequestHandler;
import com.intuit.karate.http.ServerConfig;
import com.intuit.karate.http.SslContextFactory;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.resource.ResourceUtils;
import com.intuit.karate.shell.Command;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 *
 * @author pthomas3
 */
public class Main implements Callable<Void> {

    private static final String LOGBACK_CONFIG = "logback.configurationFile";

    private static org.slf4j.Logger logger;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean help;

    @Parameters(split = "$", description = "one or more tests (features) or search-paths to run")
    List<String> paths;

    @Option(names = {"-m", "--mock", "--mocks"}, split = ",", description = "one or more mock server files")
    List<File> mocks;

    @Option(names = {"-P", "--prefix"}, description = "mock server path prefix (context-path)")
    String prefix = "/";

    @Option(names = {"-p", "--port"}, description = "server port (default 8080)")
    int port = 8080;

    @Option(names = {"-W", "--watch"}, description = "watch (and hot-reload) mock server file for changes")
    boolean watch;    

    @Option(names = {"-S", "--serve"}, description = "app server using --workdir (experimental)")
     boolean serve;

    @Option(names = {"-s", "--ssl"}, description = "use ssl / https, will use '"
            + SslContextFactory.DEFAULT_CERT_NAME + "' and '" + SslContextFactory.DEFAULT_KEY_NAME
            + "' if they exist in the working directory, or generate them")
    boolean ssl;

    @Option(names = {"-c", "--cert"}, description = "ssl certificate (default: " + SslContextFactory.DEFAULT_CERT_NAME + ")")
    File cert;

    @Option(names = {"-k", "--key"}, description = "ssl private key (default: " + SslContextFactory.DEFAULT_KEY_NAME + ")")
    File key;

    @Option(names = {"-t", "--tags"}, description = "cucumber tags - e.g. '@smoke,~@skipme' [@ignore is always skipped by default]")
    List<String> tags;

    @Option(names = {"-T", "--threads"}, description = "number of threads when running tests")
    int threads = 1;

    @Option(names = {"-o", "--output"}, description = "directory where logs and reports are output (default 'target')")
    String output = FileUtils.getBuildDir();

    @Option(names = {"-f", "--format"}, split = ",", description = "comma separate report output formats. tilde excludes the output report. html report is included by default unless it's negated."
            + "e.g. '-f json,cucumber:json,junit:xml. Possible values [html: Karate HTML, json: Karate JSON, cucumber:json: Cucumber JSON, junit:xml: JUnit XML]")
    List<String> formats;

    @Option(names = {"-n", "--name"}, description = "scenario name")
    String name;

    @Option(names = {"-e", "--env"}, description = "value of 'karate.env'")
    String env;

    @Option(names = {"-w", "--workdir"}, description = "working directory, defaults to '.'")
    File workingDir = FileUtils.WORKING_DIR;

    @Option(names = {"-g", "--configdir"}, description = "directory where 'karate-config.js' is expected (default 'classpath:' or <workingdir>)")
    String configDir;

    @Option(names = {"-C", "--clean"}, description = "clean output directory")
    boolean clean;

    @Option(names = {"-d", "--debug"}, arity = "0..1", defaultValue = "-1", fallbackValue = "0",
            description = "debug mode (optional port else dynamically chosen)")
    int debugPort;

    @Option(names = {"-D", "--dryrun"}, description = "dry run, generate html reports only")
    boolean dryRun;

    @Option(names = {"-j", "--jobserver"}, description = "job server url")
    String jobServerUrl;

    @Option(names = {"-i", "--import"}, description = "import and convert a file")
    String importFile;

    @Option(names = {"-H", "--hook"}, split = ",", description = "class name of a RuntimeHook (or RuntimeHookFactory) to add")
    List<String> hookFactoryClassNames;

    //==========================================================================
    //
    public void addPath(String path) {
        if (paths == null) {
            paths = new ArrayList();
        }
        paths.add(path);
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<String> getPaths() {
        return paths;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getThreads() {
        return threads;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isOutputHtmlReport() {
        return formats == null ? true : !formats.contains("~html");
    }

    public boolean isOutputCucumberJson() {
        return formats == null ? false : formats.contains("cucumber:json");
    }

    public boolean isOutputJunitXml() {
        return formats == null ? false : formats.contains("junit:xml");
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public static Main parseKarateOptions(String line) {
        String[] args = Command.tokenize(line);
        return CommandLine.populateCommand(new Main(), args);
    }

    // matches ( -X XXX )* (XXX)
    private static final Pattern CLI_ARGS = Pattern.compile("(\\s*-{1,2}\\w\\s\\S*\\s*)*(.*)$");

    // adds double-quotes to last positional parameter (path) in case it contains white-spaces and un-quoted
    // only if line contains just one positional parameter (path) and it is the last one in line.
    // needed for intelli-j and vs-code generated cli invocations
    public static Main parseKarateOptionsAndQuotePath(String line) {
        Matcher matcher = CLI_ARGS.matcher(line);
        if (matcher.find()) {
            String path = matcher.group(2).trim();
            if (path.contains(" ")) {
                // unquote if necessary
                String options = line.substring(0, line.lastIndexOf(path));
                path = path.replaceAll("^\"|^'|\"$|\'$", "");
                line = String.format("%s \"%s\"", options, path);
            }
        }
        return Main.parseKarateOptions(line.trim());
    }

    public Collection<RuntimeHook> createHooks() {
        if (this.hookFactoryClassNames != null) {
            return this.hookFactoryClassNames.stream()
                    .map(c -> createHook(c)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private RuntimeHook createHook(String hookClassName) {
        if (hookClassName != null) {
            try {
                Class hookClass = Class.forName(hookClassName);
                if (RuntimeHookFactory.class.isAssignableFrom(hookClass)) {
                    return ((RuntimeHookFactory) hookClass.newInstance()).create();
                } else if (RuntimeHook.class.isAssignableFrom(hookClass)) {
                    return (RuntimeHook) hookClass.newInstance();

                }
            } catch (Exception e) {
                logger.error("error instantiating RuntimeHook: {}", hookClassName, e);
            }
            logger.error("provided hook / class is not a RuntimeHook or RuntimeHookFactory: {}", hookClassName);
        }
        return null;
    }

    public static void main(String[] args) {
        boolean isClean = false;
        boolean isOutputArg = false;
        String outputDir = FileUtils.getBuildDir();
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
            System.setProperty(Constants.KARATE_OUTPUT_DIR, outputDir);
            // ensure we init logback before anything else
            String logbackConfig = System.getProperty(LOGBACK_CONFIG);
            if (StringUtils.isBlank(logbackConfig)) {
                File logbackXml = ResourceUtils.classPathOrFile("logback.xml");
                File logbackTest = ResourceUtils.classPathOrFile("logback-test.xml");
                if (logbackTest != null) {
                    System.setProperty(LOGBACK_CONFIG, "logback-test.xml");
                } else if (logbackXml != null) {
                    System.setProperty(LOGBACK_CONFIG, "logback.xml");
                } else {
                    System.setProperty(LOGBACK_CONFIG, "logback-fatjar.xml");
                }
            }
        }
        resetLoggerConfig();
        logger = LoggerFactory.getLogger("com.intuit.karate");
        logger.info("Karate version: {}", FileUtils.KARATE_VERSION);
        CommandLine cmd = new CommandLine(new Main());
        int returnCode = cmd.execute(args);
        System.exit(returnCode);
    }

    private static void resetLoggerConfig() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        try {
            Method reset = factory.getClass().getDeclaredMethod("reset");
            reset.invoke(factory);
            Class clazz = Class.forName("ch.qos.logback.classic.util.ContextInitializer");
            Object temp = clazz.getDeclaredConstructors()[0].newInstance(factory);
            Method autoConfig = clazz.getDeclaredMethod("autoConfig");
            autoConfig.invoke(temp);
        } catch (Exception e) {
            // ignore
        }
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
        if (paths != null) {
            Results results = Runner
                    .path(paths).tags(tags).scenarioName(name)
                    .karateEnv(env)
                    .workingDir(workingDir)
                    .buildDir(output)
                    .configDir(configDir)
                    .outputHtmlReport(isOutputHtmlReport())
                    .outputCucumberJson(isOutputCucumberJson())
                    .outputJunitXml(isOutputJunitXml())
                    .dryRun(dryRun)
                    .hooks(createHooks())
                    .parallel(threads);
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
        if (serve) {
            ServerConfig config = new ServerConfig(workingDir.getPath());
            RequestHandler handler = new RequestHandler(config);
            HttpServer server = HttpServer
                    .handler(handler)
                    .port(port)
                    .corsEnabled(true)                    
                    .build();
            server.waitSync();
            return null;
        }
        if (mocks == null || mocks.isEmpty()) {
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
            System.setProperty(Constants.KARATE_ENV, env);
        }
        MockServer.Builder builder = MockServer
                .featureFiles(mocks)
                .pathPrefix(prefix)
                .certFile(cert)
                .keyFile(key)
                .watch(watch);
        if (ssl) {
            builder.https(port);
        } else {
            builder.http(port);
        }
        MockServer server = builder.build();
        server.waitSync();
        return null;
    }

}
