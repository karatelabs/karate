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

import com.intuit.karate.ScriptValue;
import com.intuit.karate.XmlUtils;
import static com.intuit.karate.http.Cookie.*;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import com.jayway.jsonpath.DocumentContext;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
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
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.w3c.dom.Document;

/**
 *
 * @author pthomas3
 */
public class JerseyHttpClient extends HttpClient<Entity> {

    private Client client;
    private WebTarget target;
    private Builder builder;

    @Override
    public void configure(HttpConfig config) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .register(new LoggingInterceptor()) // must be first
                .register(MultiPartFeature.class);
        if (config.isSslEnabled()) {
            String sslAlgorithm = config.getSslAlgorithm();
            logger.info("ssl enabled, initializing generic trusted certificate / key-store with algorithm: {}", sslAlgorithm);
            SSLContext ssl = HttpUtils.getSslContext(sslAlgorithm);
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
            clientBuilder.sslContext(ssl);
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
    public String getUri() {
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
        return getEntity(map, mediaType);
    }

    @Override
    public Entity getEntity(List<MultiPartItem> items, String mediaType) {
        MultiPart multiPart = new MultiPart();
        for (MultiPartItem item : items) {
            if (item.getValue() == null || item.getValue().isNull()) {
                logger.warn("ignoring null multipart value for key: {}", item.getName());
                continue;
            }
            String name = item.getName();
            ScriptValue sv = item.getValue();
            if (name == null) {
                BodyPart bp;
                switch (sv.getType()) {
                    case JSON:
                        DocumentContext dc = sv.getValue(DocumentContext.class);
                        bp = new BodyPart().entity(dc.jsonString()).type(MediaType.APPLICATION_JSON_TYPE);
                        break;
                    case XML:
                        Document doc = sv.getValue(Document.class);
                        bp = new BodyPart().entity(XmlUtils.toString(doc)).type(MediaType.APPLICATION_XML_TYPE);
                        break;
                    default:
                        bp = new BodyPart().entity(sv.getValue());
                }
                multiPart.bodyPart(bp);
            } else if (sv.getType() == ScriptValue.Type.INPUT_STREAM) {
                InputStream is = (InputStream) sv.getValue();
                StreamDataBodyPart part = new StreamDataBodyPart(name, is);
                multiPart.bodyPart(part);
            } else {
                multiPart.bodyPart(new FormDataBodyPart(name, sv.getAsString()));
            }
        }
        return getEntity(multiPart, mediaType);
    }

    @Override
    public Entity getEntity(Object value, String mediaType) {
        if (value == null) {
            return null;
        }
        return Entity.entity(value, mediaType);
    }

    @Override
    public HttpResponse makeHttpRequest(String method, Entity entity, long startTime) {
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
        HttpResponse response = new HttpResponse();
        response.setTime(responseTime);
        response.setUri(getUri());
        response.setBody(bytes);
        response.setStatus(resp.getStatus());
        for (NewCookie c : resp.getCookies().values()) {
            com.intuit.karate.http.Cookie cookie = new com.intuit.karate.http.Cookie(c.getName(), c.getValue());
            cookie.put(DOMAIN, c.getDomain());
            cookie.put(PATH, c.getPath());
            cookie.put(EXPIRES, c.getExpiry().getTime() + "");
            cookie.put(SECURE, c.isSecure() + "");
            cookie.put(HTTP_ONLY, c.isHttpOnly() + "");
            cookie.put(MAX_AGE, c.getMaxAge() + "");
            response.addCookie(cookie);
        }
        for (Entry<String, List<Object>> entry : resp.getHeaders().entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
        return response;
    }

}
