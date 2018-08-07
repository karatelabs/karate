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
package com.intuit.karate.selenium.domain;

import com.intuit.karate.FileUtils;
import com.jayway.jsonpath.DocumentContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author vmchukky
 */

// SideProject <-> Feature (Top level feature calling other features) ???
public class SideProject extends TestBase {

    String url;
    String version;
    List<String> urls; //??? TODO: figure out url -vs- urls
    // List<String> plugins;
    List<TestSuite> suites;

    public SideProject(DocumentContext doc) {
        super(doc.read("id"), doc.read("name"));
        this.suites = new ArrayList<>();
        this.url = doc.read("$.url");
        this.version = doc.read("$.version");
        this.urls = doc.read("$.urls");
        List<Map<String, Object>> suiteList = doc.read("$.suites");
        for (Map<String, Object> suite : suiteList) {
            this.suites.add(new TestSuite(suite, doc));
        }
        // order of command execution within a testcase is kept intact, we also have order of
        // testsuites (aka features) and testcases (aka scenarios) within a suite (if needed)
        // but we loose the order of tests (without any associated testsuite) intermingled with
        // testsuites (but may be that is ok???)
        // if project has tests:[test-1, test-1-suite-1, test-2-suite-1, test-3, test-1-suite-2]
        // then the order of suites will be:
        // suite-1<test-1-sute-1,test-2-suite-1>, suite-2<test-1-suite-2>
        // and the order of tests (which are not associated with any suite) will be:
        // no-name<test-1, test-3> (and is added/executed as the last feature)???

        List<Map<String, Object>> testsWithoutAnySuite = doc.read("$.tests");
        if (testsWithoutAnySuite != null) {
            List<TestCase> testsInDefaultSuite = new ArrayList<>();
            for (Map<String, Object> test : testsWithoutAnySuite) {
                testsInDefaultSuite.add(new TestCase(test));
            }
            if (!testsInDefaultSuite.isEmpty()) {
                this.suites.add(new TestSuite(testsInDefaultSuite));
            }
        }
    }

    public String getUrl() {
        return url;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getUrls() {
        return urls;
    }

    public List<TestSuite> getSuites() {
        return suites;
    }

    public String convert(File dir, String configJson) {
        StringBuilder sb = new StringBuilder("Feature: Selenium IDE Project - ")
                .append(name).append("\n\tid = ").append(id).append("\n\tconfig = ")
                .append(configJson).append("\n\n")
                .append("Scenario: Wrapper for Project\n")
                .append("* json ").append(DRIVER_CONFIG_VAR)
                .append(" = ").append(configJson).append('\n')
                .append("* string ").append(DRIVER_URL_VAR).append(" = ")
                .append(DRIVER_CONFIG_VAR).append(".driverUrl + '/session'\n")
                .append("Given url ").append(DRIVER_URL_VAR).append("\n")
                .append("And request { desiredCapabilities: { caps: {browserName: '#(")
                .append(DRIVER_CONFIG_VAR).append(".browser)'} } }\n")
                .append("When method POST \n").append("Then status 200\n")
                .append("And assert response.status == 0\n").append("* print response\n");

        sb.append("* string ").append(DRIVER_SESSION_ID_VAR).append(" = response.sessionId\n")
                .append("* string ").append(DRIVER_SESSION_URL_VAR).append(" = ")
                .append(DRIVER_URL_VAR).append(" + '/' + ").append(DRIVER_SESSION_ID_VAR).append("\n")
                .append("* json driverParams = {")
                .append(DRIVER_SESSION_ID_VAR).append(":'#(").append(DRIVER_SESSION_ID_VAR).append(")',")
                .append(DRIVER_SESSION_URL_VAR).append(":'#(").append(DRIVER_SESSION_URL_VAR).append(")'}");

        String testUrl;
        int index = 0;
        String featureName;
        for (TestSuite suite : suites) {
            featureName = suite.getIdentifierName() + ".feature";
            // need to double check this logic
            testUrl = (index < urls.size()) ? urls.get(index++) : url;
            FileUtils.writeToFile(new File(dir, featureName), suite.convert(testUrl));
            sb.append("\n# calling testsuite ").append(suite.name);
            sb.append("\n* json featureResponse = call read('./").append(featureName).append("') ");
            sb.append("driverParams\n");
        }

        return sb.toString();
    }

}
