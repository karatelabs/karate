/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import static com.intuit.karate.http.Cookie.*;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Map.Entry;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

/**
 *
 * @author pthomas3
 */
public class JerseyHttpClient extends HttpClient<Entity> {

    private Client client;
    private WebTarget target;
    private Builder builder;

    @Override
    public void configure(HttpConfig config, ScriptContext context) {
        ClientConfig cc = new ClientConfig();
        // support request body for DELETE (non-standard)
        cc.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
        if (!config.isFollowRedirects()) {
            cc.property(ClientProperties.FOLLOW_REDIRECTS, false);
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .withConfig(cc)
                .register(new LoggingInterceptor(context)) // must be first
                .register(MultiPartFeature.class);
        if (config.isSslEnabled()) {
            SSLContext sslContext;
            if (config.getSslTrustStore() != null) {
                String trustStoreFile = config.getSslTrustStore();                
                String password = config.getSslTrustStorePassword();
                char[] passwordChars = password == null ? null : password.toCharArray();
                String algorithm = config.getSslAlgorithm();
                String type = config.getSslTrustStoreType();
                if (type == null) {
                    type = KeyStore.getDefaultType();
                }
                try {
                    KeyStore trustStore = KeyStore.getInstance(type);
                    InputStream is = FileUtils.getFileStream(trustStoreFile, context);
                    trustStore.load(is, passwordChars);
                    context.logger.debug("trust store key count: {}", trustStore.size());
                    sslContext = SslConfigurator.newInstance()
                            .securityProtocol(algorithm) // will default to TLS if null
                            .trustStore(trustStore)
                            // .keyStore(trustStore)
                            .createSSLContext();
                } catch (Exception e) {
                    context.logger.error("ssl config failed: {}", e.getMessage());
                    throw new RuntimeException(e);
                }                
            } else {
                sslContext = HttpUtils.getSslContext(config.getSslAlgorithm());
            }
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
    public String getRequestUri() {
        return target.getUri().toString();
    }

    @Override
    public void buildUrl(String url) {
        target = client.target(url);
        builder = target.request();
    }

    @Override
    public void buildPath(String path) {
        target = target.path(path);
        builder = target.request();
    }

    @Override
    public void buildParam(String name, Object... values) {
        target = target.queryParam(name, values);
        builder = target.request();
    }

    @Override
    public void buildHeader(String name, Object value, boolean replace) {
        if (replace) {
            builder.header(name, null);
        }
        builder.header(name, value);
    }

    @Override
    public void buildCookie(com.intuit.karate.http.Cookie c) {
        Cookie cookie = new Cookie(c.getName(), c.getValue());
        builder.cookie(cookie);
    }

    @Override
    public Entity getEntity(MultiValuedMap fields, String mediaType) {
        MultivaluedHashMap<String, Object> map = new MultivaluedHashMap<>();
        for (Entry<String, List> entry : fields.entrySet()) {
            map.put(entry.getKey(), entry.getValue());
        }
        return Entity.entity(map, mediaType);
    }

    @Override
    public Entity getEntity(List<MultiPartItem> items, String mediaType) {
        MultiPart multiPart = new MultiPart();
        for (MultiPartItem item : items) {
            if (item.getValue() == null || item.getValue().isNull()) {
                continue;
            }
            String name = item.getName();
            String filename = item.getFilename();
            ScriptValue sv = item.getValue();
            String ct = item.getContentType();                    
            if (ct == null) {
                ct = HttpUtils.getContentType(sv);
            }
            MediaType itemType = MediaType.valueOf(ct);
            if (name == null) { // most likely multipart/mixed
                BodyPart bp = new BodyPart().entity(sv.getAsString()).type(itemType);
                multiPart.bodyPart(bp);
            } else if (filename != null) {
                StreamDataBodyPart part = new StreamDataBodyPart(name, sv.getAsStream(), filename, itemType);
                multiPart.bodyPart(part);
            } else {
                multiPart.bodyPart(new FormDataBodyPart(name, sv.getAsString(), itemType));
            }
        }
        return Entity.entity(multiPart, mediaType);
    }

    @Override
    public Entity getEntity(String value, String mediaType) {
        return Entity.entity(value, mediaType);
    }
    
    @Override
    public Entity getEntity(InputStream value, String mediaType) {
        return Entity.entity(value, mediaType);
    }    

    @Override
    public HttpResponse makeHttpRequest(Entity entity, long startTime) {
        String method = request.getMethod();
        if ("PATCH".equals(method)) { // http://danofhisword.com/dev/2015/09/04/Jersey-Client-Http-Patch.html
            builder.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
        }
        Response resp;
        if (entity != null) {
            resp = builder.method(method, entity);
        } else {
            resp = builder.method(method);
        }
        byte[] bytes = resp.readEntity(byte[].class);
        long responseTime = getResponseTime(startTime);
        HttpResponse response = new HttpResponse(responseTime);
        response.setUri(getRequestUri());
        response.setBody(bytes);
        response.setStatus(resp.getStatus());
        for (NewCookie c : resp.getCookies().values()) {
            com.intuit.karate.http.Cookie cookie = new com.intuit.karate.http.Cookie(c.getName(), c.getValue());
            cookie.put(DOMAIN, c.getDomain());
            cookie.put(PATH, c.getPath());
            if (c.getExpiry() != null) {
                cookie.put(EXPIRES, c.getExpiry().getTime() + "");
            }
            cookie.put(SECURE, c.isSecure() + "");
            cookie.put(HTTP_ONLY, c.isHttpOnly() + "");
            cookie.put(MAX_AGE, c.getMaxAge() + "");
            response.addCookie(cookie);
        }
        for (Entry<String, List<Object>> entry : resp.getHeaders().entrySet()) {
            response.putHeader(entry.getKey(), entry.getValue());
        }
        return response;
    }

}
