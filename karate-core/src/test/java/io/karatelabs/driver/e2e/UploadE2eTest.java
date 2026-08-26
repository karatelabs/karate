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
package io.karatelabs.driver.e2e;

import io.karatelabs.driver.DriverException;
import io.karatelabs.driver.e2e.support.DriverTestBase;
import io.karatelabs.driver.e2e.support.UploadFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * inputFile() against the upload page — pins the {@code objectId(locator)} →
 * {@code DOM.setFileInputFiles} path via the Java API. The status divs echo
 * "name:content" read back through the File API, so a pass proves the browser
 * read the file's bytes off its own filesystem (fixtures are copied into the
 * container at the host-resolved path).
 *
 * <p>Path-reference resolution here runs OUTSIDE a scenario, so a bare ref is
 * classloader/CWD-based — the read()-style feature-relative resolution is
 * covered by upload.feature (CDP and W3C lanes).</p>
 */
class UploadE2eTest extends DriverTestBase {

    @BeforeAll
    static void copyFixtures() {
        UploadFixtures.copyInto(shared.getChrome());
    }

    @BeforeEach
    void navigateToUploadPage() {
        driver.setUrl(testUrl("/upload"));
    }

    @Test
    void testSingleFileViaClasspathRef() {
        driver.inputFile("#file-upload", "classpath:" + UploadFixtures.SAMPLE);
        driver.waitForText("#single-status", "upload-sample.txt:hello-upload");
    }

    @Test
    void testMultipleFilesViaFilePrefix() {
        String a = "file:" + UploadFixtures.hostPath(UploadFixtures.SAMPLE);
        String b = "file:" + UploadFixtures.hostPath(UploadFixtures.EXTRA);
        driver.inputFile("#file-multi", a, b);
        driver.waitForText("#multi-status", "2 files");
        String status = driver.text("#multi-status");
        assertTrue(status.contains("upload-sample.txt:hello-upload"), status);
        assertTrue(status.contains("upload-extra.txt:second-file"), status);
    }

    @Test
    void testHiddenFileInput() {
        // file inputs are routinely display:none behind a styled button — inputFile
        // must not require visibility
        driver.inputFile("#file-hidden", "classpath:" + UploadFixtures.SAMPLE);
        driver.waitForText("#hidden-status", "upload-sample.txt:hello-upload");
    }

    @Test
    void testElementChaining() {
        driver.locate("#file-upload").inputFile("classpath:" + UploadFixtures.SAMPLE);
        driver.waitForText("#single-status", "upload-sample.txt:hello-upload");
    }

    @Test
    void testMissingFileFailsWithResolvedPath() {
        DriverException e = assertThrows(DriverException.class,
                () -> driver.inputFile("#file-upload", "does-not-exist.txt"));
        assertTrue(e.getMessage().contains("file not found"), e.getMessage());
        assertTrue(e.getMessage().contains("does-not-exist.txt"), e.getMessage());
    }

    @Test
    void testMissingClasspathRefFails() {
        DriverException e = assertThrows(DriverException.class,
                () -> driver.inputFile("#file-upload", "classpath:no/such/fixture.txt"));
        assertTrue(e.getMessage().contains("file not found"), e.getMessage());
    }

}
