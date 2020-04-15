package payment.mock.servlet;

import com.intuit.karate.http.HttpRequestBuilder;
import com.intuit.karate.mock.servlet.MockHttpClient;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        context.register(MockMvcConfig.class);
        context.setServletContext(SERVLET_CONTEXT);
        DispatcherServlet servlet = new DispatcherServlet(context);
        ServletConfig servletConfig = new MockServletConfig();
        try {
            servlet.init(servletConfig);
        } catch (Exception e) {
            logger.error("init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return servlet;
    }

    public static MockSpringMvcServlet getMock() {
        return new MockSpringMvcServlet(SERVLET, SERVLET_CONTEXT);
    }

}
