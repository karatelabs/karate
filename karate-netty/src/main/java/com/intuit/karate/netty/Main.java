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
package com.intuit.karate.netty;

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptBindings;
import com.intuit.karate.StringUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.ui.App;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 *
 * @author pthomas3
 */
public class Main implements Callable<Void> {

    private static final String LOGBACK_CONFIG = "logback.configurationFile";
    private static final String CERT_FILE = "cert.pem";
    private static final String KEY_FILE = "key.pem";

    private static Logger logger;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean help;

    @Option(names = {"-m", "--mock"}, description = "mock server file")
    File mock;

    @Option(names = {"-p", "--port"}, description = "mock server port (required for --mock)")
    Integer port;

    @Option(names = {"-s", "--ssl"}, description = "use ssl / https, will use '"
            + CERT_FILE + "' and '" + KEY_FILE + "' if they exist in the working directory, or generate them")
    boolean ssl;

    @Option(names = {"-c", "--cert"}, description = "ssl certificate (default: " + CERT_FILE + ")")
    File cert;

    @Option(names = {"-k", "--key"}, description = "ssl private key (default: " + KEY_FILE + ")")
    File key;

    @Option(names = {"-t", "--test"}, description = "run feature file as Karate test")
    File test;

    @Option(names = {"-e", "--env"}, description = "value of 'karate.env'")
    String env;

    @Option(names = {"-u", "--ui"}, description = "show user interface")
    boolean ui;

    @Option(names = {"-a", "--args"}, description = "variables as key=value pair arguments")
    Map<String, Object> args;

    public static void main(String[] args) {
        // ensure we init logback before anything else
        String logbackConfig = System.getProperty(LOGBACK_CONFIG);
        if (StringUtils.isBlank(logbackConfig)) {        
            System.setProperty(LOGBACK_CONFIG, "logback-netty.xml");
        }
        logger = LoggerFactory.getLogger(Main.class);
        CommandLine.call(new Main(), System.err, args);
    }

    @Override
    public Void call() throws Exception {
        if (test != null) {
            if (ui) {
                App.main(new String[]{test.getAbsolutePath(), env});
            } else {
                if (env != null) {
                    System.setProperty(ScriptBindings.KARATE_ENV, env);
                }
                String configPath = System.getProperty(ScriptBindings.KARATE_CONFIG);
                if (configPath == null) {
                    System.setProperty(ScriptBindings.KARATE_CONFIG, new File(ScriptBindings.KARATE_CONFIG_JS).getPath() + "");
                }
                CucumberRunner.runFeature(test, args, true);
            }
            return null;
        } else if (ui || mock == null) {
            App.main(new String[]{});
            return null;
        }
        if (mock != null) {
            if (port == null) {
                System.err.println("--port required for --mock option");
                CommandLine.usage(this, System.err);
                return null;
            }
        }
        FeatureServer server;
        if (cert != null) {
            ssl = true;
        }
        if (ssl) {
            if (cert == null) {
                cert = new File(CERT_FILE);
                key = new File(KEY_FILE);
            }
            if (!cert.exists() || !key.exists()) {
                logger.warn("ssl requested, but " + CERT_FILE + " and/or " + KEY_FILE + " not found in working directory, will create");
                try {
                    SelfSignedCertificate ssc = new SelfSignedCertificate();
                    FileUtils.copy(ssc.certificate(), cert);
                    FileUtils.copy(ssc.privateKey(), key);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                logger.info("ssl on, using existing files: {} and {}", CERT_FILE, KEY_FILE);
            }
            server = FeatureServer.start(mock, port, cert, key, null);
        } else {
            server = FeatureServer.start(mock, port, false, null);
        }
        server.waitSync();
        return null;
    }

}
