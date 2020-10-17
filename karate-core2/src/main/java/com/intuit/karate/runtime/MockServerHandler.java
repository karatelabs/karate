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
package com.intuit.karate.runtime;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.server.Request;
import com.intuit.karate.server.Response;
import com.intuit.karate.server.ServerHandler;

/**
 *
 * @author pthomas3
 */
public class MockServerHandler implements ServerHandler {
    
    private final Feature feature;
    private final ScenarioRuntime runtime;
    
    public MockServerHandler(Feature feature) {
        this.feature = feature;
        Scenario dummy = new Scenario(feature, feature.getSection(0), -1);
        SuiteRuntime suite = new SuiteRuntime();
        FeatureRuntime featureRuntime = new FeatureRuntime(suite, feature, true);
        runtime = new ScenarioRuntime(featureRuntime, dummy);
    }
    
    @Override
    public Response handle(Request req) {
        return null;
    }
    
}
