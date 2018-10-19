/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.formats.selenium;

import com.jayway.jsonpath.DocumentContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vmchukky
 */

// TestSuite <-> Feature ???
public class TestSuite extends TestBase {
    final int timeOut;
    final boolean parallel;
    final List<TestCase> tests;

    public TestSuite(Map<String, Object> suiteJson, DocumentContext doc) {
        super(suiteJson);
        this.tests = new ArrayList<>();
        this.timeOut = (int) suiteJson.get("timeout");
        this.parallel = (boolean) suiteJson.get("parallel");
        List<String> testIds = (List) suiteJson.get("tests");
        for (String testId : testIds) {
            String jsonpath = "$.tests[?(@.id == '" + testId.trim() + "')]";
            List<Map<String, Object>> testCases = doc.read(jsonpath);
            this.tests.add(new TestCase(testCases.get(0)));
            doc.delete(jsonpath); // one way to mark parsed tests
        }
    }

    public TestSuite(List<TestCase> tests) {
        super("default-test-suite", "no-name");
        this.timeOut = 0;
        this.tests = tests;
        this.parallel = false;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public boolean isParallel() {
        return parallel;
    }

    public List<TestCase> getTests() {
        return tests;
    }

    public String convert(String url, HashMap<String, String> variables) {
        StringBuilder sb = new StringBuilder("@ignore\nFeature: ").append(getIdentifierName())
                .append("\n\turl = ").append(url).append("\n\twith parameters\n\t\t")
                .append(DRIVER_SESSION_ID).append(", ").append(DRIVER_SESSION_URL);

        for (TestCase test : tests) {
            sb.append("\n").append(test.convert(url, variables));
        }
        return sb.toString();
    }
}
