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

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static void printUsage() {
        logger.info("usage: featureFile port");
        logger.info("usage: (ssl, auto-generated cert): featureFile port ssl");
        logger.info("usage: (ssl, using cert): featureFile port certFile privateKeyFile");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
        }
        try {
            File featureFile = new File(args[0]);
            int port = Integer.valueOf(args[1]);
            FeatureServer server;
            if (args.length > 3) {
                File certFile = new File(args[2]);
                File privateKeyFile = new File(args[3]);
                server = FeatureServer.start(featureFile, port, certFile, privateKeyFile, null);
            } else if(args.length > 2) {
                server = FeatureServer.start(featureFile, port, true, null);
            } else {
                server = FeatureServer.start(featureFile, port, false, null);
            }
            server.waitSync();
        } catch (Exception e) {
            printUsage();
            throw new RuntimeException(e);
        }
    }

}
