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
package com.intuit.karate.core;

import com.intuit.karate.http.HttpServer;
import com.intuit.karate.http.HttpServerHandler;
import com.intuit.karate.http.Request;
import com.intuit.karate.http.Response;
import com.intuit.karate.http.ServerHandler;
import com.intuit.karate.http.SslContextFactory;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class MockServer extends HttpServer {

    private MockServer(ServerBuilder sb) {
        super(sb);
    }

    public static class Builder {

        Builder(Feature feature) {
            this.feature = feature;
        }

        final Feature feature;
        int port;
        boolean ssl;
        boolean watch;
        File certFile;
        File keyFile;
        Map<String, Object> args;
        
        public Builder watch(boolean value) {
            watch = value;
            return this;
        }

        public Builder http(int value) {
            port = value;
            return this;
        }

        public Builder https(int value) {
            ssl = true;
            port = value;
            return this;
        }

        public Builder certFile(File value) {
            certFile = value;
            return this;
        }

        public Builder keyFile(File value) {
            keyFile = value;
            return this;
        }

        public Builder args(Map<String, Object> value) {
            args = value;
            return this;
        }

        public Builder arg(String name, Object value) {
            if (args == null) {
                args = new HashMap();
            }
            args.put(name, value);
            return this;
        }

        public MockServer build() {
            ServerBuilder sb = Server.builder();
            sb.requestTimeoutMillis(0);
            if (ssl) {
                sb.https(port);
                SslContextFactory factory = new SslContextFactory();
                factory.setCertFile(certFile);
                factory.setKeyFile(keyFile);
                factory.build();
                sb.tls(factory.getCertFile(), factory.getKeyFile());
            } else {
                sb.http(port);
            }
            ServerHandler handler = watch ? new ReloadingMockHandler(feature, args) : new MockHandler(feature, args);
            HttpService service = new HttpServerHandler(handler);
            sb.service("prefix:/", service);
            return new MockServer(sb);
        }

    }
    
    private static class ReloadingMockHandler implements ServerHandler {
                
        private final Map<String, Object> args;
        private MockHandler handler;
        private final File file;
        private long lastModified;

        public ReloadingMockHandler(Feature feature, Map<String, Object> args) {
            file = feature.getResource().getFile();
            if (file != null) {
                lastModified = file.lastModified();
                logger.debug("watch mode init - last modified: {}", lastModified);
            }
            this.args = args;
            handler = new MockHandler(feature, args);
            
        }                

        @Override
        public Response handle(Request request) {
            if (file != null) {              
                if (file.lastModified() > lastModified) {
                    logger.info("watch mode - reloading file");
                    lastModified = file.lastModified();
                    handler = new MockHandler(Feature.read(file), args);
                }
            }
            return handler.handle(request);
        }
        
    }
  
    public static Builder feature(String path) {
        return new Builder(Feature.read(path));
    }

    public static Builder feature(File file) {
        return new Builder(Feature.read(file));
    }

    public static Builder feature(Feature feature) {
        return new Builder(feature);
    }

}
