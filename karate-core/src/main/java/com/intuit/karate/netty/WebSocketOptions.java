/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.netty;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class WebSocketOptions {

    private final URI uri;
    private String subProtocol;
    private final int port;
    private final boolean ssl;
    private Function<String, Boolean> textHandler;
    private Function<byte[], Boolean> binaryHandler;
    private Map<String, Object> headers;
    private int maxPayloadSize = 4194304;

    public WebSocketOptions(String url) {
        this(url, null);
    }
    
    public WebSocketOptions(String url, Map<String, Object> options) {
        this.uri = URI.create(url);
        ssl = "wss".equalsIgnoreCase(uri.getScheme());
        port = uri.getPort() == -1 ? (ssl ? 443 : 80) : uri.getPort();
        if (options != null) {
            subProtocol = (String) options.get("subProtocol");
            Integer temp = (Integer) options.get("maxPayloadSize");
            if (temp != null) {
                maxPayloadSize = temp;
            }
            headers = (Map) options.get("headers");
        }
    }

    public void setTextConsumer(Consumer<String> consumer) {
        textHandler = t -> {
            consumer.accept(t);
            return false; // no async signalling, for normal use, e.g. chrome developer tools
        };
    }
    
    public URI getUri() {
        return uri;
    }

    public int getPort() {
        return port;
    }

    public boolean isSsl() {
        return ssl;
    }        

    public String getSubProtocol() {
        return subProtocol;
    }      

    public void setSubProtocol(String subProtocol) {
        this.subProtocol = subProtocol;
    }

    public Function<String, Boolean> getTextHandler() {
        return textHandler;
    }        

    public void setTextHandler(Function<String, Boolean> textHandler) {
        this.textHandler = textHandler;
    }

    public Function<byte[], Boolean> getBinaryHandler() {
        return binaryHandler;
    }

    public void setBinaryHandler(Function<byte[], Boolean> binaryHandler) {
        this.binaryHandler = binaryHandler;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

}
