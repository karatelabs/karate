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

import com.intuit.karate.Config;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.ScenarioContext;
import org.apache.http.conn.ssl.LenientSslConnectionSocketFactory;

import static com.intuit.karate.http.Cookie.*;

import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.IOException;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

/**
 * @author pthomas3
 */
public class ApacheHttpClient extends HttpClient<HttpEntity> {

    public static final String URI_CONTEXT_KEY = ApacheHttpClient.class.getName() + ".URI";

    private HttpClientBuilder clientBuilder;
    private URIBuilder uriBuilder;
    private RequestBuilder requestBuilder;
    private CookieStore cookieStore;
    private Charset charset;

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
    public void configure(Config config, ScenarioContext context) {
        clientBuilder = HttpClientBuilder.create();
        charset = config.getCharset();
        if (!config.isFollowRedirects()) {
            clientBuilder.disableRedirectHandling();
        } else { // support redirect on POST by default
            clientBuilder.setRedirectStrategy(new LaxRedirectStrategy());
        }
        clientBuilder.useSystemProperties();
        cookieStore = new BasicCookieStore();
        clientBuilder.setDefaultCookieStore(cookieStore);
        clientBuilder.setDefaultCookieSpecRegistry(LenientCookieSpec.registry());
        RequestLoggingInterceptor requestInterceptor = new RequestLoggingInterceptor(context);
        clientBuilder.addInterceptorLast(requestInterceptor);
        clientBuilder.addInterceptorLast(new ResponseLoggingInterceptor(requestInterceptor, context));
        if (config.isSslEnabled()) {
            // System.setProperty("jsse.enableSNIExtension", "false");
            String algorithm = config.getSslAlgorithm(); // could be null
            KeyStore trustStore = HttpUtils.getKeyStore(context,
                    config.getSslTrustStore(), config.getSslTrustStorePassword(), config.getSslTrustStoreType());
            KeyStore keyStore = HttpUtils.getKeyStore(context,
                    config.getSslKeyStore(), config.getSslKeyStorePassword(), config.getSslKeyStoreType());
            SSLContext sslContext;
            try {
                SSLContextBuilder builder = SSLContexts.custom()
                        .setProtocol(algorithm); // will default to TLS if null
                if (trustStore == null && config.isSslTrustAll()) {
                    builder = builder.loadTrustMaterial(new TrustAllStrategy());
                } else {
                    if (config.isSslTrustAll()) {
                        builder = builder.loadTrustMaterial(trustStore, new TrustSelfSignedStrategy());
                    } else {
                        builder = builder.loadTrustMaterial(trustStore, null); // will use system / java default
                    }
                }
                if (keyStore != null) {
                    char[] keyPassword = config.getSslKeyStorePassword() == null ? null : config.getSslKeyStorePassword().toCharArray();
                    builder = builder.loadKeyMaterial(keyStore, keyPassword);
                }
                sslContext = builder.build();
                SSLConnectionSocketFactory socketFactory;
                if (keyStore != null) {
                    socketFactory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
                } else {
                    socketFactory = new LenientSslConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
                }
                clientBuilder.setSSLSocketFactory(socketFactory);
            } catch (Exception e) {
                context.logger.error("ssl context init failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setCookieSpec(LenientCookieSpec.KARATE)
                .setConnectTimeout(config.getConnectTimeout())
                .setSocketTimeout(config.getReadTimeout());
        if (config.getLocalAddress() != null) {
            try {
                InetAddress localAddress = InetAddress.getByName(config.getLocalAddress());
                configBuilder.setLocalAddress(localAddress);
            } catch (Exception e) {
                context.logger.warn("failed to resolve local address: {} - {}", config.getLocalAddress(), e.getMessage());
            }
        }
        clientBuilder.setDefaultRequestConfig(configBuilder.build());
        SocketConfig.Builder socketBuilder = SocketConfig.custom().setSoTimeout(config.getConnectTimeout());
        clientBuilder.setDefaultSocketConfig(socketBuilder.build());
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
                if (config.getNonProxyHosts() != null) {
                    ProxySelector proxySelector = new ProxySelector() {
                        private final List<String> proxyExceptions = config.getNonProxyHosts();

                        @Override
                        public List<Proxy> select(URI uri) {
                            return Collections.singletonList(proxyExceptions.contains(uri.getHost())
                                    ? Proxy.NO_PROXY
                                    : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
                        }

                        @Override
                        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                            context.logger.info("connect failed to uri: {}", uri, ioe);
                        }
                    };
                    clientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(proxySelector));
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
        if (temp == null) {
            temp = "";
        }
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
            Arrays.stream(values)
                    .filter(Objects::nonNull)
                    .forEach(o -> uriBuilder.addParameter(name, o.toString()));
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
        return ApacheHttpUtils.getEntity(items, mediaType, charset);
    }

    @Override
    protected HttpEntity getEntity(MultiValuedMap fields, String mediaType) {
        return ApacheHttpUtils.getEntity(fields, mediaType, charset);
    }

    @Override
    protected HttpEntity getEntity(String value, String mediaType) {
        return ApacheHttpUtils.getEntity(value, mediaType, charset);
    }

    @Override
    protected HttpEntity getEntity(InputStream value, String mediaType) {
        return ApacheHttpUtils.getEntity(value, mediaType, charset);
    }

    @Override
    protected HttpResponse makeHttpRequest(HttpEntity entity, ScenarioContext context) {
        if (entity != null) {
            requestBuilder.setEntity(entity);
            requestBuilder.setHeader(entity.getContentType());
        }
        HttpUriRequest httpRequest = requestBuilder.build();
        CloseableHttpClient client = clientBuilder.build();
        BasicHttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(URI_CONTEXT_KEY, getRequestUri());
        CloseableHttpResponse httpResponse;
        byte[] bytes;
        try {
            httpResponse = client.execute(httpRequest, httpContext);
            HttpEntity responseEntity = httpResponse.getEntity();
            if (responseEntity == null || responseEntity.getContent() == null) {
                bytes = new byte[0];
            } else {
                InputStream is = responseEntity.getContent();
                bytes = FileUtils.toBytes(is);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        HttpRequest actualRequest = context.getPrevRequest();
        HttpResponse response = new HttpResponse(actualRequest.getStartTime(), actualRequest.getEndTime());
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
        cookieStore.clear(); // we rely on the StepDefs for cookie 'persistence'
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
