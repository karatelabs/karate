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
package com.intuit.karate.http.apache;

import com.intuit.karate.ScriptContext;
import org.apache.http.conn.ssl.LenientSslConnectionSocketFactory;
import com.intuit.karate.ScriptValue;
import static com.intuit.karate.http.Cookie.*;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;

/**
 *
 * @author pthomas3
 */
public class ApacheHttpClient extends HttpClient<HttpEntity> {

    public static final String URI_CONTEXT_KEY = ApacheHttpClient.class.getName() + ".URI";

    private HttpClientBuilder clientBuilder;
    private URIBuilder uriBuilder;
    private RequestBuilder requestBuilder;
    private CookieStore cookieStore;

    private void build() {
        try {
            URI uri = uriBuilder.build();
            String method = request.getMethod();
            requestBuilder = RequestBuilder.create(method).setUri(uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure(HttpConfig config, ScriptContext context) {
        clientBuilder = HttpClientBuilder.create();
        clientBuilder.useSystemProperties();
        cookieStore = new BasicCookieStore();
        clientBuilder.setDefaultCookieStore(cookieStore);
        AtomicInteger counter = new AtomicInteger();
        clientBuilder.addInterceptorLast(new RequestLoggingInterceptor(counter, context.logger));
        clientBuilder.addInterceptorLast(new ResponseLoggingInterceptor(counter, context.logger));
        if (config.isSslEnabled()) {
            // System.setProperty("jsse.enableSNIExtension", "false");
            String sslAlgorithm = config.getSslAlgorithm();
            SSLContext sslContext = HttpUtils.getSslContext(sslAlgorithm);
            SSLConnectionSocketFactory socketFactory = new LenientSslConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
            clientBuilder.setSSLSocketFactory(socketFactory);
        }

        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setConnectTimeout(config.getConnectTimeout())
                .setSocketTimeout(config.getReadTimeout());
        clientBuilder.setDefaultRequestConfig(configBuilder.build());
        if (config.getProxyUri() != null) {
            try {
                URI proxyUri = new URIBuilder(config.getProxyUri()).build();
                clientBuilder.setProxy(new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme()));
                if (config.getProxyUsername() != null && config.getProxyPassword() != null) {
                    CredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(
                            new AuthScope(proxyUri.getHost(), proxyUri.getPort()),
                            new UsernamePasswordCredentials(config.getProxyUsername(), config.getProxyPassword()));
                    clientBuilder.setDefaultCredentialsProvider(credsProvider);

                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void buildUrl(String url) {
        try {
            uriBuilder = new URIBuilder(url);
            build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void buildPath(String path) {
        String temp = uriBuilder.getPath();
        if (!temp.endsWith("/")) {
            temp = temp + "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        uriBuilder.setPath(temp + path);
        build();
    }

    @Override
    protected void buildParam(String name, Object... values) {
        if (values.length == 1) {
            Object v = values[0];
            if (v != null) {
                uriBuilder.setParameter(name, v.toString());
            }            
        } else {
            String v = StringUtils.join(values, ',');
            uriBuilder.setParameter(name, v);
        }
        build();
    }

    @Override
    protected void buildHeader(String name, Object value, boolean replace) {
        if (replace) {
            requestBuilder.removeHeaders(name);
        }
        requestBuilder.addHeader(name, value == null ? null : value.toString());
    }

    @Override
    protected void buildCookie(com.intuit.karate.http.Cookie c) {
        BasicClientCookie cookie = new BasicClientCookie(c.getName(), c.getValue());
        for (Entry<String, String> entry : c.entrySet()) {
            switch (entry.getKey()) {
                case DOMAIN:
                    cookie.setDomain(entry.getValue());
                    break;
                case PATH:
                    cookie.setPath(entry.getValue());
                    break;
            }
        }
        if (cookie.getDomain() == null) {
            cookie.setDomain(uriBuilder.getHost());
        }
        cookieStore.addCookie(cookie);
    }

    @Override
    protected HttpEntity getEntity(List<MultiPartItem> items, String mediaType) {
        boolean hasNullName = false;
        for (MultiPartItem item : items) {
            if (item.getName() == null) {
                hasNullName = true;
                break;
            }
        }
        if (hasNullName) { // multipart/related
            String boundary = HttpUtils.generateMimeBoundaryMarker();
            String text = HttpUtils.multiPartToString(items, boundary);
            ContentType ct = ContentType.create(mediaType).withParameters(new BasicNameValuePair("boundary", boundary));
            return new StringEntity(text, ct);
        } else {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create().setContentType(ContentType.create(mediaType));
            for (MultiPartItem item : items) {
                if (item.getValue() == null || item.getValue().isNull()) {
                    continue;
                }
                String name = item.getName();
                ScriptValue sv = item.getValue();
                if (name == null) {
                    // builder.addPart(bodyPart);
                } else {
                    FormBodyPartBuilder formBuilder = FormBodyPartBuilder.create().setName(name);
                    ContentBody contentBody;
                    switch (sv.getType()) {
                        case INPUT_STREAM:
                            InputStream is = (InputStream) sv.getValue();
                            contentBody = new InputStreamBody(is, ContentType.APPLICATION_OCTET_STREAM, name);
                            break;
                        case XML:
                            contentBody = new StringBody(sv.getAsString(), ContentType.APPLICATION_XML);
                            break;
                        case JSON:
                            contentBody = new StringBody(sv.getAsString(), ContentType.APPLICATION_JSON);
                            break;
                        default:
                            contentBody = new StringBody(sv.getAsString(), ContentType.TEXT_PLAIN);
                    }
                    formBuilder = formBuilder.setBody(contentBody);
                    builder = builder.addPart(formBuilder.build());
                }
            }
            return builder.build();
        }
    }

    @Override
    protected HttpEntity getEntity(MultiValuedMap fields, String mediaType) {
        List<NameValuePair> list = new ArrayList<>(fields.size());
        for (Entry<String, List> entry : fields.entrySet()) {
            String stringValue;
            List values = entry.getValue();
            if (values == null) {
                stringValue = null;
            } else if (values.size() == 1) {
                Object value = values.get(0);
                if (value == null) {
                    stringValue = null;
                } else if (value instanceof String) {
                    stringValue = (String) value;
                } else {
                    stringValue = value.toString();
                }
            } else {
                stringValue = StringUtils.join(values, ',');
            }
            list.add(new BasicNameValuePair(entry.getKey(), stringValue));
        }
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list);
            entity.setContentType(mediaType);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected HttpEntity getEntity(String value, String mediaType) {
        return new StringEntity(value, ContentType.create(mediaType));
    }

    @Override
    protected HttpEntity getEntity(InputStream value, String mediaType) {
        return new InputStreamEntity(value, ContentType.create(mediaType));
    }

    @Override
    protected HttpResponse makeHttpRequest(HttpEntity entity, long startTime) {
        if (entity != null) {
            requestBuilder.setEntity(entity);
            requestBuilder.setHeader(entity.getContentType());
        }
        HttpUriRequest httpRequest = requestBuilder.build();
        CloseableHttpClient client = clientBuilder.build();
        BasicHttpContext context = new BasicHttpContext();
        context.setAttribute(URI_CONTEXT_KEY, getRequestUri());
        CloseableHttpResponse httpResponse;
        byte[] bytes;
        try {
            httpResponse = client.execute(httpRequest, context);
            HttpEntity responseEntity = httpResponse.getEntity();
            if (responseEntity == null || responseEntity.getContent() == null) {
                bytes = new byte[0];
            } else {
                InputStream is = responseEntity.getContent();
                bytes = IOUtils.toByteArray(is);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long responseTime = getResponseTime(startTime);
        HttpResponse response = new HttpResponse();
        response.setTime(responseTime);
        response.setUri(getRequestUri());
        response.setBody(bytes);
        response.setStatus(httpResponse.getStatusLine().getStatusCode());
        for (Cookie c : cookieStore.getCookies()) {
            com.intuit.karate.http.Cookie cookie = new com.intuit.karate.http.Cookie(c.getName(), c.getValue());
            cookie.put(DOMAIN, c.getDomain());
            cookie.put(PATH, c.getPath());
            if (c.getExpiryDate() != null) {
                cookie.put(EXPIRES, c.getExpiryDate().getTime() + "");
            }
            cookie.put(PERSISTENT, c.isPersistent() + "");
            cookie.put(SECURE, c.isSecure() + "");
            response.addCookie(cookie);
        }
        for (Header header : httpResponse.getAllHeaders()) {
            response.addHeader(header.getName(), header.getValue());
        }
        return response;
    }

    @Override
    protected String getRequestUri() {
        return requestBuilder.getUri().toString();
    }

}
