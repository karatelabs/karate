/*
 * The MIT License
 *
 * Copyright 2026 Karate Labs Inc.
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
package io.karatelabs.driver.e2e.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.net.URL;
import java.nio.file.Path;

/**
 * Copies the upload fixture files into a browser container at the SAME absolute path they
 * have on the host. inputFile() resolves refs on the karate (host) side but hands the
 * resolved path to the BROWSER process — which in these tests lives in a container, so the
 * file must exist there at that exact path for the browser to actually read its bytes.
 */
public class UploadFixtures {

    public static final String SAMPLE = "io/karatelabs/driver/features/upload-sample.txt";
    public static final String EXTRA = "io/karatelabs/driver/features/upload-extra.txt";

    public static void copyInto(GenericContainer<?> container) {
        for (String fixture : new String[]{SAMPLE, EXTRA}) {
            Path hostPath = hostPath(fixture);
            container.copyFileToContainer(MountableFile.forHostPath(hostPath), hostPath.toString());
        }
    }

    /**
     * Absolute host path of a classpath fixture (in target/test-classes after the build).
     */
    public static Path hostPath(String classpathResource) {
        try {
            URL url = Thread.currentThread().getContextClassLoader().getResource(classpathResource);
            if (url == null) {
                throw new IllegalStateException("fixture not on classpath: " + classpathResource);
            }
            return Path.of(url.toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new RuntimeException("cannot resolve fixture host path: " + classpathResource, e);
        }
    }

}
