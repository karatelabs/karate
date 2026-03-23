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
package io.karatelabs.core.xml;

import io.karatelabs.common.Resource;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.FeatureRuntime;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.gherkin.Feature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for calling features with XML data.
 */
class XmlCallTest {

    @Test
    void testXmlCallFeature() {
        Resource resource = Resource.path("src/test/resources/io/karatelabs/core/xml/xml-call.feature");
        Feature feature = Feature.read(resource);
        FeatureResult result = FeatureRuntime.of(feature).call();

        assertTrue(result.isPassed(), "XML call feature should pass: " + getFailureMessage(result));
    }

    private String getFailureMessage(FeatureResult result) {
        if (result.isPassed()) return "none";
        StringBuilder sb = new StringBuilder();
        for (ScenarioResult sr : result.getScenarioResults()) {
            if (sr.isFailed()) {
                sb.append(sr.getScenario().getName()).append(": ");
                sb.append(sr.getFailureMessage()).append("\n");
            }
        }
        return sb.toString();
    }

}
