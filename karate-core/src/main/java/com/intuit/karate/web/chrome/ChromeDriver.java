/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.web.chrome;

import com.intuit.karate.Http;
import com.intuit.karate.web.Driver;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ChromeDriver implements Driver {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromeDriver.class);
    
    private final Http http;
    private final String sessionId;
    private final String windowId;
    
    public ChromeDriver(Map<String, Object> options) {
        Integer port = (Integer) options.get("port");
        if (port == null) {
            port = 9515;
        }
        String urlBase = "http://localhost:" + port;
        http = Http.forUrl(urlBase);
        sessionId = http.path("session")
                .post("{ desiredCapabilities: { browserName: 'Chrome' } }")
                .jsonPath("get[0] response..sessionId").asString();
        logger.debug("init session id: {}", sessionId);
        http.url(urlBase + "/session/" + sessionId);
        windowId = http.path("window").get().jsonPath("$.value").asString();
        logger.debug("init window id: {}", windowId);
        activate();
    }
    
    private String getElementId(String id) {
        String body;
        if (id.startsWith("/")) {
            body = "{ using: 'xpath', value: \"" + id + "\" }";
        } else if (id.startsWith("#")) {
            body = "{ using: 'id', value: '" + id.substring(1) + "' }";
        } else {
            body = "{ using: 'value', value: '" + id  + "' }";
        }
        logger.debug("body: {}", body);
        return http.path("element").post(body).jsonPath("$.value.ELEMENT").asString();
    }

    @Override
    public void location(String url) {
        http.path("url").post("{ url: '" + url + "'}");
    }

    @Override
    public void activate() {
        http.path("window").post("{ handle: '" + windowId + "' }");
    }

    @Override
    public void focus(String id) {

    }

    @Override
    public void input(String name, String value) {
        String id = getElementId(name);
        http.path("element", id, "value").post("{ value: ['" + value + "'] }");
    }

    @Override
    public void click(String name) {
        String id = getElementId(name);
        http.path("element", id, "click").post("{}");
    }

    @Override
    public void submit(String name) {
        click(name);
    }

    @Override
    public void close() {
        http.path("window").delete();
    }

    @Override
    public void stop() {
        http.delete();
    }

    @Override
    public String getLocation() {
        return http.path("url").get().jsonPath("$.value").asString();
    }

    @Override
    public String html(String locator) {
        String id = getElementId(locator);
        return http.path("element", id, "attribute", "innerHTML").get().jsonPath("$.value").asString();
    }

    @Override
    public String text(String locator) {
        String id = getElementId(locator);
        return http.path("element", id, "text").get().jsonPath("$.value").asString();
    }

}
