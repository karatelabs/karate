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
package com.intuit.karate.http;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.cors.CorsService;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class HttpServer {

    protected static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final Server server;
    private final CompletableFuture<Void> future;
    private final int port;

    public static class Builder { // TODO

        int port;
        boolean corsEnabled;
        ServerHandler handler;

        public Builder port(int value) {
            port = value;
            return this;
        }

        public Builder corsEnabled(boolean value) {
            corsEnabled = value;
            return this;
        }

        public Builder handler(ServerHandler value) {
            handler = value;
            return this;
        }

        public HttpServer build() {
            ServerBuilder sb = Server.builder();
            sb.requestTimeoutMillis(0);
            sb.http(port);
            HttpService service = new HttpServerHandler(handler);
            if (corsEnabled) {
                service = service.decorate(CorsService.builderForAnyOrigin().newDecorator());
            }
            sb.service("prefix:/", service);
            return new HttpServer(sb);
        }

    }

    public void waitSync() {
        try {
            Thread.currentThread().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getPort() {
        return port;
    }

    public CompletableFuture stop() {
        return server.stop();
    }

    public static Builder handler(ServerHandler handler) {
        return new Builder().handler(handler);
    }

    public static Builder root(String root) {
        return config(new ServerConfig(root));
    }

    public static Builder config(ServerConfig config) {
        return handler(new RequestHandler(config));
    }

    public HttpServer(ServerBuilder sb) {
        HttpService httpStopService = (ctx, req) -> {
            logger.debug("received command to stop server: {}", req.path());
            this.stop();
            return HttpResponse.of(HttpStatus.ACCEPTED);
        };
        sb.service("/__admin/stop", httpStopService);
        server = sb.build();
        future = server.start();
        future.join();
        this.port = server.activePort().localAddress().getPort();
        logger.debug("server started: {}:{}", server.defaultHostname(), this.port);
    }

}
