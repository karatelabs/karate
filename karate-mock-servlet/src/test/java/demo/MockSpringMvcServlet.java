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

import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.mock.servlet.MockHttpClient;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.web.WebMvcProperties;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 *
 * @author pthomas3
 */
public class MockSpringMvcServlet extends MockHttpClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MockSpringMvcServlet.class);

    private final Servlet servlet;
    private final ServletContext servletContext;

    public MockSpringMvcServlet(Servlet servlet, ServletContext servletContext) {
        this.servlet = servlet;
        this.servletContext = servletContext;
    }

    @Override
    protected Servlet getServlet(HttpRequestBuilder request) {
        return servlet;
    }

    @Override
    protected ServletContext getServletContext() {
        return servletContext;
    }

    private static final ServletContext SERVLET_CONTEXT = new MockServletContext();
    private static final Servlet SERVLET;

    static {
        SERVLET = initServlet();
    }

    private static Servlet initServlet() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(MockDemoConfig.class);
        context.setServletContext(SERVLET_CONTEXT);
        DispatcherServlet servlet = new DispatcherServlet(context);
        ServletConfig servletConfig = new MockServletConfig();
        try {
            servlet.init(servletConfig);
            customize(servlet);
        } catch (Exception e) {
            logger.error("init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return servlet;
    }

    // if you want things like error handling, encoding etc to work exactly as the "real" spring DispatcherServlet
    // you may need to add filters and beans to make some tests pass
    // this may not be worth it, so alternatively use tags to exclude those tests from your local mock-servlet based tests
    // note that this code below e.g. the WebMvcProperties depends on spring-boot 1.5X
    private static void customize(Servlet servlet) {
        if (servlet instanceof DispatcherServlet) {
            DispatcherServlet dispatcherServlet = (DispatcherServlet) servlet;
            WebMvcProperties mvcProperties = dispatcherServlet.getWebApplicationContext().getBean(WebMvcProperties.class);
            dispatcherServlet.setThrowExceptionIfNoHandlerFound(mvcProperties.isThrowExceptionIfNoHandlerFound());
            dispatcherServlet.setDispatchOptionsRequest(mvcProperties.isDispatchOptionsRequest());
            dispatcherServlet.setDispatchTraceRequest(mvcProperties.isDispatchTraceRequest());
        }
    }

    public static MockSpringMvcServlet getMock() {
        return new MockSpringMvcServlet(SERVLET, SERVLET_CONTEXT);
    }

}
