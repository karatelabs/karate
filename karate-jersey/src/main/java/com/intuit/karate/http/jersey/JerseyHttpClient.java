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
package com.intuit.karate.http.jersey;

import com.intuit.karate.Logger;
import com.intuit.karate.runtime.Config;
import com.intuit.karate.runtime.ScenarioEngine;
import com.intuit.karate.server.HttpClient;
import com.intuit.karate.server.HttpLogger;
import com.intuit.karate.server.HttpRequest;
import com.intuit.karate.server.Response;
import java.io.IOException;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;

/**
 *
 * @author pthomas3
 */
public class JerseyHttpClient implements HttpClient, ClientRequestFilter {

    private final ScenarioEngine engine;
    private final Logger logger;
    private final HttpLogger httpLogger;

    private Client client;

    public JerseyHttpClient(ScenarioEngine engine) {
        this.engine = engine;
        logger = engine.logger;
        httpLogger = new HttpLogger(logger);
        configure(engine.getConfig());
    }

    private void configure(Config config) {
        ClientConfig cc = new ClientConfig();
        // support request body for DELETE (non-standard)
        cc.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        if (!config.isFollowRedirects()) {
            cc.property(ClientProperties.FOLLOW_REDIRECTS, false);
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .withConfig(cc).register(this);
        if (config.isSslEnabled()) {
            String algorithm = config.getSslAlgorithm(); // could be null
            KeyStore trustStore = engine.getKeyStore(config.getSslTrustStore(), config.getSslTrustStorePassword(), config.getSslTrustStoreType());
            KeyStore keyStore = engine.getKeyStore(config.getSslKeyStore(), config.getSslKeyStorePassword(), config.getSslKeyStoreType());
            SSLContext sslContext = SslConfigurator.newInstance()
                    .securityProtocol(algorithm) // will default to TLS if null
                    .trustStore(trustStore)
                    .keyStore(keyStore)
                    .createSSLContext();
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            clientBuilder.sslContext(sslContext);
            clientBuilder.hostnameVerifier((host, session) -> true);
        }
        client = clientBuilder.build();
        client.property(ClientProperties.CONNECT_TIMEOUT, config.getConnectTimeout());
        client.property(ClientProperties.READ_TIMEOUT, config.getReadTimeout());
        if (config.getProxyUri() != null) {
            client.property(ClientProperties.PROXY_URI, config.getProxyUri());
            if (config.getProxyUsername() != null && config.getProxyPassword() != null) {
                client.property(ClientProperties.PROXY_USERNAME, config.getProxyUsername());
                client.property(ClientProperties.PROXY_PASSWORD, config.getProxyPassword());
            }
        }
    }

    @Override
    public void setConfig(Config config, String keyThatChanged) {
        configure(config);
    }

    @Override
    public Config getConfig() {
        return engine.getConfig();
    }

    private HttpRequest request;

    @Override
    public Response invoke(HttpRequest request) {
        this.request = request;
        WebTarget target = client.target(request.getUrl());
        Invocation.Builder builder = target.request();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((k, vals) -> vals.forEach(v -> builder.header(k, v)));
        }
        String method = request.getMethod();
        if ("PATCH".equals(method)) { // http://danofhisword.com/dev/2015/09/04/Jersey-Client-Http-Patch.html
            builder.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
        }
        javax.ws.rs.core.Response httpResponse;
        byte[] bytes;
        try {
            if (request.getBody() == null) {
                httpResponse = builder.method(method);
            } else {
                String contentType = request.getContentType();
                if (contentType == null) {
                    contentType = MediaType.APPLICATION_OCTET_STREAM;
                }
                httpResponse = builder.method(method, Entity.entity(request.getBody(), contentType));
            }
            bytes = httpResponse.readEntity(byte[].class);
            request.setEndTimeMillis(System.currentTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, List<String>> headers = toHeaders(httpResponse.getStringHeaders());
        Response response = new Response(httpResponse.getStatus(), headers, bytes);
        httpLogger.logResponse(getConfig(), request, response);
        return response;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        request.setHeaders(toHeaders(requestContext.getStringHeaders()));
        httpLogger.logRequest(getConfig(), request);
        request.setStartTimeMillis(System.currentTimeMillis());
    }

    private static Map<String, List<String>> toHeaders(MultivaluedMap<String, String> headers) {
        Map<String, List<String>> map = new LinkedHashMap(headers.size());
        headers.forEach((k, v) -> map.put(k, v));
        return map;
    }

}
