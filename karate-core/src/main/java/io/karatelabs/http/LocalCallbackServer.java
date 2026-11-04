package io.karatelabs.http;

import io.karatelabs.common.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Local HTTP server to capture OAuth redirect with authorization code.
 * Listens on http://127.0.0.1:PORT/callback, or on the port and path of a configured loopback redirect URI.
 * A callback whose {@code state} is not {@link #getState()} is refused and leaves the code future pending (RFC 6749 §10.12).
 */
public class LocalCallbackServer {

    private static final Logger logger = LoggerFactory.getLogger(LocalCallbackServer.class);

    private HttpServer server;
    private CompletableFuture<String> codeFuture;
    private int port;
    private String path = "/callback";
    private final String state = UUID.randomUUID().toString();

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

    /**
     * Start server on the port of {@code redirectUri}, receiving the code at its path.
     * @return {@code redirectUri} as given
     */
    public String start(URI redirectUri) {
        String uriPath = redirectUri.getPath();
        path = uriPath == null || uriPath.isEmpty() ? "/" : uriPath;
        start(redirectUri.getPort() < 0 ? 80 : redirectUri.getPort());
        return redirectUri.toString();
    }

    private HttpResponse handleRequest(HttpRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return HttpResponse.text(405, "Method Not Allowed");
        }

        if (!path.equals(request.getPath())) {
            return HttpResponse.text(404, "Not Found");
        }

        String code = request.getParam("code");
        String error = request.getParam("error");

        logger.debug("Received OAuth callback: code={}, error={}",
            code != null ? "present" : "null", error);

        HttpResponse response = new HttpResponse();
        if (!state.equals(request.getParam("state"))) {
            logger.warn("OAuth callback ignored: its state is not the one this authorization sent");
            sendErrorPage(response, "invalid_state", "This callback was not sent for this sign-in, so it was ignored.");
        } else if (code != null) {
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
        response.setBody(html, ResourceType.HTML);
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
        response.setBody(html, ResourceType.HTML);
    }

    /** The {@code state} the authorization request sends; only a callback carrying it is accepted. */
    public String getState() {
        return state;
    }

    public CompletableFuture<String> getCodeFuture() {
        return codeFuture;
    }

    public int getPort() {
        return port;
    }

    public void stop() {
        stopAndWait();
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
