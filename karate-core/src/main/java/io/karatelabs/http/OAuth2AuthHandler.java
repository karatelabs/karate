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
 * An OAuth2 grant: {@link #apply} sends its token as {@code Authorization: Bearer <token>}; a caller that places the
 * token itself (a query param, another header prefix) asks for it with {@link #getAccessToken} instead.
 */
public interface OAuth2AuthHandler extends AuthHandler {

    /** The current access token, fetched or refreshed when needed; {@code builder} is the request it is for, left unchanged. */
    String getAccessToken(HttpRequestBuilder builder);

    @Override
    default void apply(HttpRequestBuilder builder) {
        builder.header("Authorization", "Bearer " + getAccessToken(builder));
    }

    @Override
    default String getType() {
        return "oauth2";
    }

}
