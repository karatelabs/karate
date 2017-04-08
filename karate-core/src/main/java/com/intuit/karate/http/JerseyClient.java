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
package com.intuit.karate.http;

import com.intuit.karate.KarateException;
import com.intuit.karate.LoggingFilter;
import com.intuit.karate.RequestFilter;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.ScriptValue;
import com.intuit.karate.SslUtils;
import com.intuit.karate.XmlUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class JerseyClient implements HttpClient {

    private static final Logger logger = LoggerFactory.getLogger(JerseyClient.class);

    private Client client;

    @Override
    public void configure(HttpConfig config) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .register(new LoggingFilter()) // must be first
                .register(MultiPartFeature.class)
                .register(new RequestFilter());
        if (config.isSslEnabled()) {
            String sslAlgorithm = config.getSslAlgorithm();
            logger.info("ssl enabled, initializing generic trusted certificate / key-store with algorithm: {}", sslAlgorithm);
            SSLContext ssl = SslUtils.getSslContext(sslAlgorithm);
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
            clientBuilder.sslContext(ssl);
            clientBuilder.hostnameVerifier((host, session) -> true);
        }
        client = clientBuilder.build();
        client.property(ClientProperties.CONNECT_TIMEOUT, config.getConnectTimeout());
        client.property(ClientProperties.READ_TIMEOUT, config.getReadTimeout());
        if (config.getProxyUri() != null) {
            client.property(ClientProperties.PROXY_URI, config.getProxyUri());
        }
        if (config.getProxyUsername() != null) {
            client.property(ClientProperties.PROXY_USERNAME, config.getProxyUsername());
        }
        if (config.getProxyPassword() != null) {
            client.property(ClientProperties.PROXY_PASSWORD, config.getProxyPassword());
        }
    }

    @Override
    public HttpResponse invoke(HttpRequest request, ScriptContext context) {
        String url = request.getUrl();
        if (url == null) {
            String msg = "url not set, please refer to the keyword documentation for 'url'";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        WebTarget target = client.target(url);
        if (request.getPaths() != null) {
            for (String path : request.getPaths()) {
                target = target.path(path);
            }
        }
        if (request.getParams() != null) {
            for (Entry<String, List> entry : request.getParams().entrySet()) {
                target = target.queryParam(entry.getKey(), entry.getValue().toArray());
            }
        }
        Builder builder = target.request();
        builder.property(ScriptContext.KARATE_DOT_CONTEXT, context);
        if (request.getHeaders() != null) {
            for (Entry<String, List> entry : request.getHeaders().entrySet()) {
                for (Object value : entry.getValue()) {
                    builder.header(entry.getKey(), value);
                }
            }
        }
        if (request.getCookies() != null) {
            for (Entry<String, String> entry : request.getCookies().entrySet()) {
                builder.cookie(entry.getKey(), entry.getValue());
            }
        }
        String method = request.getMethod();
        if (method == null) {
            String msg = "'method' is required to make an http call";
            logger.error(msg);
            throw new RuntimeException(msg);
        }
        method = method.toUpperCase();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            if ("PATCH".equals(method)) { // http://danofhisword.com/dev/2015/09/04/Jersey-Client-Http-Patch.html
                builder.property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
            }
            String mediaType = request.getContentType();
            if (request.getMultiPartItems() != null) {
                if (mediaType == null) {
                    mediaType = MediaType.MULTIPART_FORM_DATA;
                }
                MultiPart mp = getMultiPart(request);
                return makeHttpRequest(target, builder, method, Entity.entity(mp, mediaType));
            } else if (request.getFormFields() != null) {
                MultivaluedHashMap<String, Object> map = new MultivaluedHashMap<>();
                for (Entry<String, List> entry : request.getFormFields().entrySet()) {
                    map.put(entry.getKey(), entry.getValue());
                }
                return makeHttpRequest(target, builder, method, Entity.entity(map, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
            } else {
                ScriptValue body = request.getBody();
                if (body == null || body.isNull()) {
                    String msg = "request body is requred for a " + method + ", please use the 'request' keyword";
                    logger.error(msg);
                    throw new RuntimeException(msg);
                }
                return makeHttpRequest(target, builder, method, getEntity(body, mediaType));
            }
        } else {
            return makeHttpRequest(target, builder, method, null);
        }
    }

    private static MultiPart getMultiPart(HttpRequest request) {
        MultiPart mp = new MultiPart();
        for (MultiPartItem item : request.getMultiPartItems()) {
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
                mp.bodyPart(bp);
            } else if (sv.getType() == ScriptValue.Type.INPUT_STREAM) {
                InputStream is = (InputStream) sv.getValue();
                StreamDataBodyPart part = new StreamDataBodyPart(name, is);
                mp.bodyPart(part);
            } else {
                mp.bodyPart(new FormDataBodyPart(name, sv.getAsString()));
            }
        }
        return mp;
    }

    private static Entity getEntity(ScriptValue body, String mediaType) {
        switch (body.getType()) {
            case JSON:
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_JSON;
                }
                DocumentContext json = body.getValue(DocumentContext.class);
                return Entity.entity(json.jsonString(), mediaType);
            case MAP:
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_JSON;
                }                
                Map<String, Object> map = body.getValue(Map.class);
                DocumentContext doc = JsonPath.parse(map);
                return Entity.entity(doc.jsonString(), mediaType);
            case XML:
                Node node = body.getValue(Node.class);
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_XML;
                }
                return Entity.entity(XmlUtils.toString(node), mediaType);
            case INPUT_STREAM:
                InputStream is = body.getValue(InputStream.class);
                if (mediaType == null) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
                return Entity.entity(is, mediaType);
            default:
                if (mediaType == null) {
                    mediaType = MediaType.TEXT_PLAIN;
                }
                return Entity.entity(body.getAsString(), mediaType);
        }
    }

    private static HttpResponse makeHttpRequest(WebTarget target, Builder builder, String method, Entity entity) {
        long startTime = System.currentTimeMillis();
        Response resp;
        try {
            if (entity != null) {
                resp = builder.method(method, entity);
            } else {
                resp = builder.method(method);
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            String message = "http call failed after " + responseTime + " milliseconds for URL: " + target.getUri();
            logger.error(e.getMessage() + ", " + message);
            throw new KarateException(message, e);
        }
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        logger.debug("response time in milliseconds: {}", responseTime);
        byte[] bytes = resp.readEntity(byte[].class);
        HttpResponse response = new HttpResponse();
        response.setUri(target.getUri().toString());
        response.setBody(bytes);
        response.setStatus(resp.getStatus());
        response.setResponseTime(responseTime);
        for (Map.Entry<String, NewCookie> entry : resp.getCookies().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().getValue();
            response.addCookie(key, value);
            logger.trace("set cookie: {} - {}", key, entry.getValue());
        }
        for (Entry<String, List<Object>> entry : resp.getHeaders().entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
        return response;
    }

}
