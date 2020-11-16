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
package demo;

import com.intuit.karate.mock.servlet.MockHttpClient;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpClientFactory;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 *
 * @author pthomas3
 */
public class MockSpringMvcServlet implements HttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(MockSpringMvcServlet.class);

    private final ServletContext servletContext;
    private final DispatcherServlet servlet;

    public MockSpringMvcServlet() {
        servletContext = new MockServletContext();
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(MockDemoConfig.class);
        context.setServletContext(servletContext);
        servlet = new DispatcherServlet(context);
        ServletConfig servletConfig = new MockServletConfig();
        try {
            servlet.init(servletConfig);
            // if you want things like error handling, encoding etc to work exactly as the "real" spring DispatcherServlet
            // you may need to add filters and beans to make some tests pass
            // this may not be worth it, so alternatively use tags to exclude those tests from your local mock-servlet based tests
            // note that this code below e.g. the WebMvcProperties depends on spring-boot 1.5X            
            WebMvcProperties mvcProperties = servlet.getWebApplicationContext().getBean(WebMvcProperties.class);
            servlet.setThrowExceptionIfNoHandlerFound(mvcProperties.isThrowExceptionIfNoHandlerFound());
            servlet.setDispatchOptionsRequest(mvcProperties.isDispatchOptionsRequest());
            servlet.setDispatchTraceRequest(mvcProperties.isDispatchTraceRequest());
        } catch (Exception e) {
            logger.error("init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public HttpClient create(ScenarioEngine engine) {
        return new MockHttpClient(engine, servlet, servletContext);
    }

}
