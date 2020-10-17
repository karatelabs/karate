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

import com.intuit.karate.server.HttpServerHandler;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class MockHttpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(MockHttpServer.class);

    private final Server server;
    private final CompletableFuture<Void> future;   
    
    public void waitSync() {
        future.join();
        try {
            Thread.currentThread().join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    
    
    public MockHttpServer(int port, MockServerHandler handler) {
        ServerBuilder sb = Server.builder();
        sb.http(port);
        sb.service("prefix:/", new HttpServerHandler(handler));
        server = sb.build();
        future = server.start();
        logger.debug("server started: {}:{}", server.defaultHostname(), port);        
    }
    
}
