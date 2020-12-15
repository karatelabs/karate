/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class SslContextFactory {

    private static final Logger logger = LoggerFactory.getLogger(SslContextFactory.class);

    public static final String DEFAULT_CERT_NAME = "cert.pem";
    public static final String DEFAULT_KEY_NAME = "key.pem";

    private String buildDir;
    private File certFile;
    private File keyFile;

    public void setBuildDir(String buildDir) {
        this.buildDir = buildDir;
    }

    public void setCertFile(File certFile) {
        this.certFile = certFile;
    }

    public void setKeyFile(File keyFile) {
        this.keyFile = keyFile;
    }

    public File getCertFile() {
        return certFile;
    }

    public File getKeyFile() {
        return keyFile;
    }

    public void build() {
        if (buildDir == null) {
            buildDir = FileUtils.getBuildDir();
        }
        try {
            if (certFile == null || keyFile == null) {
                // attempt to re-use as far as possible
                certFile = new File(buildDir + File.separator + DEFAULT_CERT_NAME);
                keyFile = new File(buildDir + File.separator + DEFAULT_KEY_NAME);
            }
            if (!certFile.exists() || !keyFile.exists()) {
                logger.warn("ssl - " + certFile + " and / or " + keyFile + " not found, will create");
                HttpUtils.createSelfSignedCertificate(certFile, keyFile);
            } else {
                logger.info("ssl - re-using existing files: {} and {}", certFile, keyFile);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
