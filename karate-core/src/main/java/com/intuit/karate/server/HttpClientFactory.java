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
package com.intuit.karate.server;

import com.intuit.karate.Resource;
import com.intuit.karate.runtime.ScenarioEngine;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
@FunctionalInterface
public interface HttpClientFactory {

    HttpClient create(ScenarioEngine engine);

    public static final HttpClientFactory DEFAULT = engine -> new ArmeriaHttpClient(engine.getConfig(), engine.logger);

    static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);
    
    static final String KARATE_HTTP_PROPERTIES = "classpath:karate-http.properties";
    static final String CLIENT_FACTORY = "client.factory";

    public static HttpClientFactory resolveClientFactory(ClassLoader classLoader) {
        try {
            Resource resource = new Resource(KARATE_HTTP_PROPERTIES, classLoader);
            InputStream is = resource.getStream();
            if (is == null) {
                throw new RuntimeException(KARATE_HTTP_PROPERTIES + " not found");
            }
            Properties props = new Properties();
            props.load(is);
            String className = props.getProperty(CLIENT_FACTORY);
            if (className == null) {
                throw new RuntimeException("property " + CLIENT_FACTORY + " not found in " + KARATE_HTTP_PROPERTIES);
            }
            Class clazz = Class.forName(className);
            HttpClientFactory factory = (HttpClientFactory) clazz.newInstance();
            logger.info("using http client factory: {}", factory.getClass());
            return factory;
        } catch (Exception e) {
            logger.warn("using built-in http client, {}", e.getMessage());
            return HttpClientFactory.DEFAULT;
        }
    }

}
