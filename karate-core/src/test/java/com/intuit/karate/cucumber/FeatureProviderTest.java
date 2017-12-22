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
package com.intuit.karate.cucumber;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.ScriptValueMap;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureProviderTest {

    private static final Logger logger = LoggerFactory.getLogger(FeatureProviderTest.class);

    private Map<String, Object> getRequest(String name) {
        return Match.init()
                .defText("name", name)
                .def("cat", "{ name: #(name) }")
                .eval("{ request: '#(cat)' }").asMap();
    }

    @Test
    public void testServer() {
        File file = FileUtils.getFileRelativeTo(getClass(), "server.feature");
        FeatureWrapper featureWrapper = FeatureWrapper.fromFile(file);
        FeatureProvider provider = new FeatureProvider(featureWrapper);
        ScriptValueMap vars = provider.handle(getRequest("Billie"));
        Match.equals(vars.get("response").getAsMap(), "{ id: 1, name: 'Billie' }");
        vars = provider.handle(getRequest("Wild"));
        Match.equals(vars.get("response").getAsMap(), "{ id: 2, name: 'Wild' }");
        List<Map> list = vars.get("cats").getAsList();
        Match.equals(list, "[{ id: 1, name: 'Billie' }, { id: 2, name: 'Wild' }]");
    }

}
