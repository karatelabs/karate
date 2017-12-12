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
import com.intuit.karate.JsonUtils;
import com.intuit.karate.ScriptValueMap;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureServerTest {

    private static final Logger logger = LoggerFactory.getLogger(FeatureServerTest.class);

    private Map<String, Object> getRequest(String name) {
        Map<String, Object> cat = JsonUtils.toJsonDoc("{ name: '" + name + "' }").read("$");
        Map<String, Object> args = new HashMap();
        args.put("request", cat);
        return args;
    }

    @Test
    public void testServer() {
        File file = FileUtils.getFileRelativeTo(getClass(), "server.feature");
        FeatureWrapper featureWrapper = FeatureWrapper.fromFile(file);
        FeatureServer server = new FeatureServer(featureWrapper);
        Map<String, Object> init = new HashMap();
        init.put("currentId", 0);
        init.put("cats", new ArrayList());
        server.initVars(init);
        ScriptValueMap vars = server.handle(getRequest("Billie"));
        Map<String, Object> response1 = vars.get("response").getAsMap();
        assertEquals(1, response1.get("id"));
        assertEquals("Billie", response1.get("name"));
        vars = server.handle(getRequest("Wild"));
        Map<String, Object> response2 = vars.get("response").getAsMap();
        assertEquals(2, response2.get("id"));
        assertEquals("Wild", response2.get("name"));
        List<Map> list = vars.get("cats").getAsList();
        assertEquals(2, list.size());
        assertEquals(response1, list.get(0));
        assertEquals(response2, list.get(1));
    }

}
