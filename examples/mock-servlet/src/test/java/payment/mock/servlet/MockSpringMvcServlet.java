package payment.mock.servlet;

import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.mock.servlet.MockHttpClient;
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

    private final DispatcherServlet servlet;
    private final ServletContext servletContext;

    public MockSpringMvcServlet() {
        servletContext = new MockServletContext();
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(MockMvcConfig.class);
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
