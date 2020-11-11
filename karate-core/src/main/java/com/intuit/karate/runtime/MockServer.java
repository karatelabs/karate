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
package com.intuit.karate.runtime;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.server.HttpServer;
import com.intuit.karate.server.HttpServerHandler;
import com.intuit.karate.server.SslContextFactory;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.cors.CorsService;
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
        boolean corsEnabled;
        File certFile;
        File keyFile;
        Map<String, Object> args;

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

        public Builder corsEnabled() {
            corsEnabled = true;
            return this;
        }

        public MockServer build() {
            ServerBuilder sb = Server.builder();
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
            MockHandler mockHandler = new MockHandler(feature, args);
            HttpService service = new HttpServerHandler(mockHandler);
            if (corsEnabled) {
                service = service.decorate(CorsService.builderForAnyOrigin().newDecorator());
            }
            sb.service("prefix:/", service);
            return new MockServer(sb);
        }

    }

    public static Builder feature(String path) {
        return new Builder(FeatureParser.parse(path));
    }

    public static Builder feature(File file) {
        return new Builder(FeatureParser.parse(file));
    }

    public static Builder feature(Feature feature) {
        return new Builder(feature);
    }

}
