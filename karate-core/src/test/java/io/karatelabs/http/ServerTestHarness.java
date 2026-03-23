package io.karatelabs.http;

import io.karatelabs.common.Resource;
import io.karatelabs.js.Engine;
import io.karatelabs.markup.Markup;
import io.karatelabs.markup.RootResourceResolver;
import io.karatelabs.markup.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Test harness for full integration testing of HTTP server with JS evaluation
 * and HTML template rendering.
 * <p>
 * Designed to be reused across test classes with server instance sharing
 * to avoid slow Netty startup/shutdown cycles.
 * <p>
 * Usage:
 * <pre>
 * static ServerTestHarness harness;
 *
 * &#64;BeforeAll
 * static void beforeAll() {
 *     harness = new ServerTestHarness("classpath:templates");
 *     harness.start();
 * }
 *
 * &#64;AfterAll
 * static void afterAll() {
 *     harness.stop();
 * }
 *
 * &#64;Test
 * void testJsEval() {
 *     harness.setHandler(ctx -> {
 *         ctx.eval("response.body = 'hello ' + request.param('name')");
 *         return ctx.response();
 *     });
 *     HttpResponse response = harness.get("/test?name=world");
 *     assertEquals("hello world", response.getBodyString());
 * }
 * </pre>
 */
public class ServerTestHarness implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ServerTestHarness.class);

    private final String resourceRoot;
    private final ResourceResolver resolver;
    private HttpServer server;
    private HttpClient client;
    private Function<RequestContext, HttpResponse> handler;
    private int port;
    private Session sharedSession;

    public ServerTestHarness(String resourceRoot) {
        this.resourceRoot = resourceRoot;
        this.resolver = new RootResourceResolver(resourceRoot);
    }

    public ServerTestHarness(ResourceResolver resolver) {
        this.resourceRoot = null;
        this.resolver = resolver;
    }

    public void start() {
        sharedSession = Session.inMemory();
        server = HttpServer.start(0, this::handleRequest);
        port = server.getPort();
        client = new ApacheHttpClient();
        logger.debug("ServerTestHarness started on port {}", port);
    }

    public void stop() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("error closing client: {}", e.getMessage());
            }
        }
        if (server != null) {
            server.stopAsync();
        }
        logger.debug("ServerTestHarness stopped");
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Set the handler for incoming requests.
     * The handler receives a RequestContext with pre-configured JS engine,
     * request, response, and session objects.
     */
    public void setHandler(Function<RequestContext, HttpResponse> handler) {
        this.handler = handler;
    }

    private HttpResponse handleRequest(HttpRequest request) {
        if (handler == null) {
            HttpResponse response = new HttpResponse();
            response.setStatus(404);
            response.setBody("no handler configured");
            return response;
        }
        RequestContext ctx = new RequestContext(request, resolver, sharedSession);
        try {
            return handler.apply(ctx);
        } catch (Exception e) {
            logger.error("handler error: {}", e.getMessage(), e);
            HttpResponse response = new HttpResponse();
            response.setStatus(500);
            response.setBody("error: " + e.getMessage());
            return response;
        }
    }

    // Convenience methods for making requests

    public HttpResponse get(String path) {
        return request("GET", path, null);
    }

    public HttpResponse post(String path, Object body) {
        return request("POST", path, body);
    }

    public HttpResponse request(String method, String path, Object body) {
        HttpRequestBuilder builder = new HttpRequestBuilder(client);
        builder.url(getBaseUrl());

        // Parse query params from path using HttpUtils utility
        builder.path(HttpUtils.extractPath(path));
        Map<String, List<String>> params = HttpUtils.parseQueryParams(path);
        params.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                builder.param(name, values.toArray(new String[0]));
            }
        });

        builder.method(method);
        if (body != null) {
            builder.body(body);
        }
        return builder.invoke();
    }

    /**
     * Context provided to test handlers with access to JS engine,
     * request/response/session objects, and template rendering.
     */
    public static class RequestContext {

        private final HttpRequest request;
        private final HttpResponse response;
        private final Session session;
        private final Engine engine;
        private final ResourceResolver resolver;
        private Markup markup;

        public RequestContext(HttpRequest request, ResourceResolver resolver) {
            this(request, resolver, Session.inMemory());
        }

        public RequestContext(HttpRequest request, ResourceResolver resolver, Session session) {
            this.request = request;
            this.response = new HttpResponse();
            this.session = session;
            this.engine = new Engine();
            this.resolver = resolver;

            // Bind objects to JS engine
            engine.put("request", request);
            engine.put("response", response);
            engine.put("session", session);
        }

        public HttpRequest request() {
            return request;
        }

        public HttpResponse response() {
            return response;
        }

        public Session session() {
            return session;
        }

        public Engine engine() {
            return engine;
        }

        /**
         * Evaluate JavaScript with access to request/response/session.
         */
        public Object eval(String js) {
            return engine.eval(js);
        }

        /**
         * Evaluate JavaScript with additional variables.
         */
        public Object evalWith(String js, Map<String, Object> vars) {
            return engine.evalWith(js, vars);
        }

        /**
         * Evaluate a JavaScript file from classpath or file system.
         * The file is evaluated as a block with request/response/session available.
         *
         * @param resourcePath path to JS file (supports "classpath:" prefix)
         * @return evaluation result
         */
        public Object evalFile(String resourcePath) {
            Resource resource = Resource.path(resourcePath);
            String js = resource.getText();
            return engine.eval(js);
        }

        /**
         * Get or create the Markup template engine.
         */
        public Markup markup() {
            if (markup == null) {
                markup = Markup.init(engine, resolver);
            }
            return markup;
        }

        /**
         * Render an HTML template with the given variables.
         * The request, response, and session are automatically available.
         */
        public String renderTemplate(String templatePath, Map<String, Object> vars) {
            Map<String, Object> allVars = new HashMap<>();
            allVars.put("request", request);
            allVars.put("response", response);
            allVars.put("session", session);
            if (vars != null) {
                allVars.putAll(vars);
            }
            return markup().processPath(templatePath, allVars);
        }

        /**
         * Render an HTML template with no additional variables.
         */
        public String renderTemplate(String templatePath) {
            return renderTemplate(templatePath, null);
        }

        /**
         * Render an inline HTML string with the given variables.
         */
        public String renderString(String html, Map<String, Object> vars) {
            Map<String, Object> allVars = new HashMap<>();
            allVars.put("request", request);
            allVars.put("response", response);
            allVars.put("session", session);
            if (vars != null) {
                allVars.putAll(vars);
            }
            return markup().processString(html, allVars);
        }

        /**
         * Render an inline HTML string with no additional variables.
         */
        public String renderString(String html) {
            return renderString(html, null);
        }

        /**
         * Set response body to rendered template and return response.
         */
        public HttpResponse respondWithTemplate(String templatePath, Map<String, Object> vars) {
            String html = renderTemplate(templatePath, vars);
            response.setBody(html);
            response.setHeader("Content-Type", "text/html");
            return response;
        }

        /**
         * Set response body to rendered template and return response.
         */
        public HttpResponse respondWithTemplate(String templatePath) {
            return respondWithTemplate(templatePath, null);
        }

    }

}
