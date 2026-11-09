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

import io.karatelabs.driver.e2e.support.DriverTestBase;
import io.karatelabs.driver.e2e.support.SharedChromeContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code switchChildFrame} enters a frame nested in the current one: a same-process child, and one inside an
 * out-of-process iframe (the shape of an Electron webview: a cross-site frame holding a same-origin frame).
 */
class NestedFrameE2eTest extends DriverTestBase {

    @AfterEach
    void backToMain() {
        driver.switchFrame(null);
    }

    @Test
    void aFrameInsideAnOutOfProcessFrameIsEnteredInItsMainWorld() {
        driver.setUrl(testUrl("/nested-frames"));
        String other = shared.getChrome().getCrossOriginHostAccessUrl(SharedChromeContainer.TEST_SERVER_PORT);
        driver.script("document.getElementById('outer').src = '" + other + "/nested-outer.html'");

        driver.switchFrame("#outer");
        assertEquals("outer", driver.script("window.outerValue"));
        driver.switchChildFrame("#inner");
        assertEquals("inner", driver.script("window.innerValue"), "the page's own world, not an isolated one");
        driver.click("#inner-btn");
        assertEquals("clicked", driver.text("#inner-out"));

        driver.switchFrame(null);
        assertEquals("parent", driver.script("window.parentValue"));
    }

    @Test
    void aSameProcessNestedFrameIsEntered() {
        driver.setUrl(testUrl("/nested-frames"));

        driver.switchChildFrame("#same");
        assertEquals("outer", driver.script("window.outerValue"));
        driver.switchChildFrame("#inner");
        assertEquals("inner", driver.script("window.innerValue"));
        assertEquals("Inner", driver.text("#inner-btn"));
    }

}
