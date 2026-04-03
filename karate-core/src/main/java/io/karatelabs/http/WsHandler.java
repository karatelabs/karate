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
package io.karatelabs.http;

/**
 * Handler for WebSocket upgrade requests.
 * Called when a client sends an HTTP request with "Upgrade: websocket".
 * The handler receives the original HTTP request and an open WebSocket connection.
 */
@FunctionalInterface
public interface WsHandler {

    /**
     * Handle a WebSocket connection after the upgrade handshake completes.
     * This method is called on a Netty worker thread. Implementations should
     * register message callbacks on the connection and return promptly.
     *
     * @param request    the HTTP request that initiated the WebSocket upgrade
     * @param connection the WebSocket connection for sending and receiving messages
     */
    void accept(HttpRequest request, WsConnection connection);

}
