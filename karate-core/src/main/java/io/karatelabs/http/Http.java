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

import io.karatelabs.common.Json;
import io.karatelabs.core.KarateConfig;
import io.karatelabs.output.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent HTTP client API for making HTTP requests from Java code.
 * <p>
 * Example usage:
 * <pre>
 * HttpResponse response = Http.to("https://api.example.com")
 *     .path("users")
 *     .header("Authorization", "Bearer token")
 *     .get();
 * </pre>
 */
public class Http implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Http.class);

    public final String urlBase;
    private final HttpClient client;
    private final HttpRequestBuilder builder;
    private final KarateConfig config = new KarateConfig();
    private boolean quiet;
    private Map<String, Object> challenge; // the client config's auth the armed handler answers a challenge with

    public static Http to(String url) {
        return new Http(url);
    }

    private Http(String urlBase) {
        this.urlBase = urlBase;
        this.client = new DefaultHttpClientFactory().create();
        this.builder = new HttpRequestBuilder(client).configSupplier(() -> config);
        builder.url(urlBase);
    }

    public Http url(String url) {
        builder.url(url);
        return this;
    }

    public Http param(String key, String... values) {
        builder.param(key, values);
        return this;
    }

    public Http path(String... paths) {
        builder.paths(paths);
        return this;
    }

    public Http header(String name, String value) {
        builder.header(name, value);
        return this;
    }

    public Http body(Object body) {
        builder.body(body);
        return this;
    }

    public Http contentType(String contentType) {
        builder.contentType(contentType);
        return this;
    }

    public Http configure(String key, Object value) {
        config.configure(key, value);
        client.apply(config);
        return this;
    }

    public Http configure(Map<String, Object> map) {
        map.forEach(config::configure);
        client.apply(config);
        return this;
    }

    /**
     * Signs the next request only: {@code handler} is applied to it, and the client writes its {@code Host} from the
     * URI, dropping any {@code Host} header set on it. A {@link DigestAuthHandler} or {@link NtlmAuthHandler} adds no
     * header: for that request its credentials replace the client config's {@code auth}, whether set before or after
     * this call, for the transport to answer the server's challenge, scoped to the request's host. When the request
     * returns or throws, the handler is dropped and the config's {@code auth} is back to what it was when the request
     * started, which rebuilds the client and so closes any connection the challenge authenticated. Null disarms a
     * handler not yet used.
     */
    public Http auth(AuthHandler handler) {
        disarm();
        builder.auth(handler);
        builder.hostFromUri(handler != null);
        challenge = challengeAuth(handler);
        return this;
    }

    private static Map<String, Object> challengeAuth(AuthHandler handler) {
        Map<String, Object> auth = new LinkedHashMap<>();
        if (handler instanceof DigestAuthHandler digest) {
            auth.put("type", "digest");
            auth.put("username", digest.getUsername());
            auth.put("password", digest.getPassword());
        } else if (handler instanceof NtlmAuthHandler ntlm) {
            auth.put("type", "ntlm");
            auth.put("username", ntlm.getUsername());
            auth.put("password", ntlm.getPassword());
            auth.put("domain", ntlm.getDomain());
            auth.put("workstation", ntlm.getWorkstation());
        } else {
            return null;
        }
        return auth;
    }

    private void disarm() {
        builder.auth(null);
        builder.hostFromUri(false);
        challenge = null;
    }

    /** The URI the next request goes to: the URL with the paths and params set so far, as the client sends it. */
    public String getUri() {
        return builder.getUri();
    }

    /** Callers that treat a 4xx/5xx as a normal outcome and do their own logging. */
    public Http quiet() {
        quiet = true;
        return this;
    }

    public HttpResponse method(String method, Object body) {
        if (body != null) {
            builder.body(body instanceof Json ? ((Json) body).value() : body);
        }
        builder.method(method);
        Map<String, Object> restore = null;
        HttpResponse response;
        try {
            if (challenge != null) {
                restore = new LinkedHashMap<>(config.getAuth());
                config.configure("auth", challenge);
                client.apply(config);
            }
            response = builder.invoke();
        } finally {
            disarm();
            if (restore != null) {
                config.configure("auth", restore.isEmpty() ? null : restore);
                client.apply(config);
            }
        }
        if (response.getStatus() >= 400 && (quiet ? logger.isDebugEnabled() : logger.isWarnEnabled())) {
            String line = LogContext.get().filterHttpLog("http response code: " + response.getStatus()
                    + ", response: " + response.getBodyString() + ", url: " + builder.getUri());
            if (line != null) {
                if (quiet) {
                    logger.debug(line);
                } else {
                    logger.warn(line);
                }
            }
        }
        return response;
    }

    public HttpResponse method(String method) {
        return method(method, null);
    }

    public HttpResponse methodJson(String method, String body) {
        return method(method, Json.of(body));
    }

    public HttpResponse get() {
        return method("GET");
    }

    public HttpResponse post(Object body) {
        return method("POST", body);
    }

    public HttpResponse postJson(String body) {
        return post(Json.of(body).value());
    }

    public HttpResponse put(Object body) {
        return method("PUT", body);
    }

    public HttpResponse putJson(String body) {
        return put(Json.of(body).value());
    }

    public HttpResponse patch(Object body) {
        return method("PATCH", body);
    }

    public HttpResponse patchJson(String body) {
        return patch(Json.of(body).value());
    }

    public HttpResponse delete() {
        return method("DELETE");
    }

    public HttpResponse delete(Object body) {
        return method("DELETE", body);
    }

    /** Releases this facade's client: its connections and the credentials its config holds. */
    @Override
    public void close() {
        disarm();
        try {
            client.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
