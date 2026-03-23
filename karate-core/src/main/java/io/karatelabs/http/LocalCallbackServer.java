package io.karatelabs.http;

import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Local HTTP server to capture OAuth redirect with authorization code.
 * Listens on http://127.0.0.1:PORT/callback
 */
public class LocalCallbackServer {

    private static final Logger logger = LoggerFactory.getLogger(LocalCallbackServer.class);

    private HttpServer server;
    private CompletableFuture<String> codeFuture;
    private int port;

    /**
     * Start server on random available port
     */
    public String start() {
        return start(0);
    }

    /**
     * Start server on specific port (0 = random)
     * @return The redirect URI: http://127.0.0.1:PORT/callback
     */
    public String start(int preferredPort) {
        codeFuture = new CompletableFuture<>();

        server = HttpServer.start(preferredPort, this::handleRequest);
        port = server.getPort();

        logger.debug("OAuth callback server started on port {}", port);

        return "http://127.0.0.1:" + port + "/callback";
    }

    private HttpResponse handleRequest(HttpRequest request) {
        HttpResponse response = new HttpResponse();

        // Only handle GET requests to /callback path
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(405);
            response.setBody("Method Not Allowed");
            return response;
        }

        if (!"/callback".equals(request.getPath())) {
            response.setStatus(404);
            response.setBody("Not Found");
            return response;
        }

        String code = request.getParam("code");
        String error = request.getParam("error");
        String state = request.getParam("state");

        logger.debug("Received OAuth callback: code={}, error={}, state={}",
            code != null ? "present" : "null", error, state);

        if (code != null) {
            codeFuture.complete(code);
            sendSuccessPage(response);
        } else {
            String errorDesc = request.getParam("error_description");
            if (errorDesc == null) {
                errorDesc = "Unknown error";
            }
            codeFuture.completeExceptionally(
                new OAuth2Exception(error + ": " + errorDesc)
            );
            sendErrorPage(response, error, errorDesc);
        }

        return response;
    }

    private void sendSuccessPage(HttpResponse response) {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Authorization Successful</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                    h1 { color: #4caf50; }
                    p { color: #666; }
                </style>
            </head>
            <body>
                <h1>✓ Authorization Successful</h1>
                <p>You can close this window and return to the application.</p>
            </body>
            </html>
            """;
        response.setStatus(200);
        response.setBody(html);
        response.setContentType("text/html; charset=UTF-8");
    }

    private void sendErrorPage(HttpResponse response, String error, String description) {
        String html = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>Authorization Failed</title>
                <style>
                    body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }
                    h1 { color: #f44336; }
                    p { color: #666; }
                    code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; }
                </style>
            </head>
            <body>
                <h1>✗ Authorization Failed</h1>
                <p><strong>Error:</strong> <code>%s</code></p>
                <p>%s</p>
            </body>
            </html>
            """, error, description);
        response.setStatus(400);
        response.setBody(html);
        response.setContentType("text/html; charset=UTF-8");
    }

    public CompletableFuture<String> getCodeFuture() {
        return codeFuture;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        stopAsync();
    }

    public void stopAsync() {
        if (server != null) {
            logger.debug("Stopping OAuth callback server on port {}", port);
            server.stopAsync();
        }
    }

    public void stopAndWait() {
        if (server != null) {
            logger.debug("Stopping OAuth callback server on port {}", port);
            server.stopAndWait();
        }
    }
}
