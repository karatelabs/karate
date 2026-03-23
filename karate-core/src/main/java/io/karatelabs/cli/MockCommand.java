/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.cli;

import io.karatelabs.output.Console;
import io.karatelabs.core.MockServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * The 'mock' subcommand for starting mock servers.
 * <p>
 * Usage examples:
 * <pre>
 * # Start mock server on dynamic port
 * karate mock -m api.feature
 *
 * # Start mock server on specific port
 * karate mock -m api.feature -p 8080
 *
 * # Multiple mock files
 * karate mock -m users.feature -m orders.feature -p 8080
 *
 * # With HTTPS
 * karate mock -m api.feature -p 8443 --ssl
 *
 * # With custom certificate
 * karate mock -m api.feature -p 8443 --ssl --cert cert.pem --key key.pem
 * </pre>
 */
@Command(
        name = "mock",
        mixinStandardHelpOptions = true,
        description = "Start a mock server from feature files"
)
public class MockCommand implements Callable<Integer> {

    @Option(
            names = {"-m", "--mock"},
            description = "Mock feature file (can be specified multiple times)",
            required = true
    )
    List<String> mockFiles;

    @Option(
            names = {"-p", "--port"},
            description = "Port to listen on (default: 0 = dynamic)"
    )
    Integer port = 0;

    @Option(
            names = {"-s", "--ssl"},
            description = "Enable HTTPS"
    )
    boolean ssl;

    @Option(
            names = {"-c", "--cert"},
            description = "SSL certificate file (PEM format)"
    )
    String certPath;

    @Option(
            names = {"-k", "--key"},
            description = "SSL private key file (PEM format)"
    )
    String keyPath;

    @Option(
            names = {"--path-prefix"},
            description = "URL path prefix to strip from incoming requests"
    )
    String pathPrefix;

    @Option(
            names = {"-W", "--watch"},
            description = "Enable hot-reload when feature files change"
    )
    boolean watch;

    @Override
    public Integer call() {
        try {
            Console.println(Console.info("Starting mock server..."));

            // Build server with first feature file
            MockServer.Builder builder = MockServer.feature(mockFiles.get(0));

            // Add additional feature files if specified
            for (int i = 1; i < mockFiles.size(); i++) {
                builder.feature(mockFiles.get(i));
            }

            // Apply options
            builder.port(port);

            if (ssl) {
                builder.ssl(true);
                if (certPath != null) {
                    builder.certPath(certPath);
                }
                if (keyPath != null) {
                    builder.keyPath(keyPath);
                }
            }

            if (pathPrefix != null) {
                builder.pathPrefix(pathPrefix);
            }

            if (watch) {
                builder.watch(true);
            }

            // Start server
            MockServer server = builder.start();

            String protocol = ssl ? "https" : "http";
            Console.println();
            Console.println(Console.pass("Mock server started:"));
            Console.println("  URL: " + protocol + "://localhost:" + server.getPort());
            Console.println("  Features: " + String.join(", ", mockFiles));

            if (ssl) {
                Console.println("  SSL: enabled" + (certPath != null ? " (custom certificate)" : " (self-signed)"));
            }

            if (watch) {
                Console.println("  Watch: enabled (hot-reload on file change)");
            }

            Console.println();
            Console.println(Console.yellow("Press Ctrl+C to stop the server"));

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Console.println();
                Console.println(Console.warn("Shutting down mock server..."));
                server.stopAndWait();
                Console.println(Console.pass("Mock server stopped."));
            }));

            // Wait for server to be stopped
            server.waitSync();

            return 0;
        } catch (Exception e) {
            Console.println(Console.fail("Error starting mock server: " + e.getMessage()));
            return 1;
        }
    }

}
