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
package mock.jersey;

import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.mock.servlet.MockHttpClient;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

/**
 *
 * @author pthomas3
 */
public class MockJerseyServlet extends MockHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(MockJerseyServlet.class);

    private final Servlet servlet;
    private final ServletContext servletContext;

    /**
     * this zero-arg constructor will be invoked if you use the 'configure
     * httpClientClass' option refer to the MockJerseyServletFactory for how you
     * can construct this manually and have full control over
     * dependency-injection specific to your environment
     *
     * @throws Exception
     */
    public MockJerseyServlet() throws Exception {
        logger.info("auto construction of mock http servlet");
        ServletConfig servletConfig = new MockServletConfig();
        servletContext = new MockServletContext();
        ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class);
        servlet = new ServletContainer(resourceConfig);
        servlet.init(servletConfig);
    }

    public MockJerseyServlet(Servlet servlet, ServletContext servletContext) {
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

}
