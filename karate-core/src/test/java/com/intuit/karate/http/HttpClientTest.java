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
package com.intuit.karate.http;

import com.intuit.karate.Config;
import com.intuit.karate.CallContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.FeatureContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class HttpClientTest {
    
    private ScenarioContext getContext() {
        FeatureContext featureContext = FeatureContext.forEnv();
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }    
    
    @Test
    public void testSwappingHttpClient() {
        Config config = new Config();
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        config.configure("userDefined", new ScriptValue(map));
        config.setClientClass("com.intuit.karate.http.CustomDummyHttpClient");
        HttpClient client = HttpClient.construct(config, getContext());
        HttpResponse response = client.makeHttpRequest(null, null);
        assertArrayEquals(response.getBody(), "hello John".getBytes());        
    }
    
}
