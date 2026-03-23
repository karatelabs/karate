/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.driver.cdp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CdpLauncherTest {

    @Test
    void testDefaultPathsMac() {
        assertEquals("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                CdpLauncher.DEFAULT_PATH_MAC);
    }

    @Test
    void testDefaultPathsWindows() {
        assertEquals("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                CdpLauncher.DEFAULT_PATH_WIN64);
        assertEquals("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                CdpLauncher.DEFAULT_PATH_WIN32);
    }

    @Test
    void testDefaultPathsLinux() {
        assertEquals("/usr/bin/google-chrome",
                CdpLauncher.DEFAULT_PATH_LINUX);
    }

}
