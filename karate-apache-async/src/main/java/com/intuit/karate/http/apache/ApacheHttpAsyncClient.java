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

import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptContext;
import com.intuit.karate.http.*;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.async.methods.AsyncRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.*;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.intuit.karate.http.Cookie.*;

/**
 * @author dkumar
 */
public class ApacheHttpAsyncClient extends HttpClient<HttpEntity> {

    public static final String URI_CONTEXT_KEY = ApacheHttpAsyncClient.class.getName() + ".URI";

    private HttpAsyncClientBuilder clientBuilder;
    private URIBuilder uriBuilder;
    private AsyncRequestBuilder requestBuilder;
    private CookieStore cookieStore;
    private Charset charset;
    private SimpleHttpRequest simpleHttpRequest;

    private void build() {
        try {
            URI uri = uriBuilder.build();
            String method = request.getMethod();
            requestBuilder = AsyncRequestBuilder.create(method).setUri(uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure(HttpConfig config, ScriptContext context) {
        clientBuilder = HttpAsyncClientBuilder.create();
        charset = config.getCharset();
        if (!config.isFollowRedirects()) {
            clientBuilder.disableRedirectHandling();
        } else { // support redirect on POST by default
            clientBuilder.setRedirectStrategy(new DefaultRedirectStrategy());
        }
        clientBuilder.useSystemProperties();
        cookieStore = new BasicCookieStore();
        clientBuilder.setDefaultCookieStore(cookieStore);
        clientBuilder.setDefaultCookieSpecRegistry(LenientCookieSpec.registry());
        RequestLoggingInterceptor requestInterceptor = new RequestLoggingInterceptor(context);
        clientBuilder.addResponseInterceptorLast(requestInterceptor);
        clientBuilder.addResponseInterceptorLast(new ResponseLoggingInterceptor(requestInterceptor, context));

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
            } catch (Exception e) {
                context.logger.error("ssl context init failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
            SSLConnectionSocketFactory socketFactory = new LenientSslConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
            //clientBuilder.setSSLSocketFactory(socketFactory);
        }
        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setCookieSpec(LenientCookieSpec.KARATE)
                .setConnectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS);
                //.setSocketTimeout(config.getReadTimeout());
        clientBuilder.setDefaultRequestConfig(configBuilder.build());
        if (config.getProxyUri() != null) {
            try {
                URI proxyUri = new URIBuilder(config.getProxyUri()).build();
                clientBuilder.setProxy(new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme()));
                if (config.getProxyUsername() != null && config.getProxyPassword() != null) {
                    CredentialsStore credsStore = new BasicCredentialsProvider();
                    credsStore.setCredentials(
                            new AuthScope(proxyUri.getHost(), proxyUri.getPort()),
                            new UsernamePasswordCredentials(config.getProxyUsername(), config.getProxyPassword().toCharArray()));
                    clientBuilder.setDefaultCredentialsProvider(credsStore);
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
    protected HttpResponse makeHttpRequest(HttpEntity entity, ScriptContext context) {
        if (entity != null) {
            try {
                byte[] bytes = IOUtils.toByteArray(entity.getContent());
                requestBuilder.setEntity(new BasicAsyncEntityProducer(bytes, ContentType.getByMimeType(entity.getContentType()) ));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        AsyncRequestProducer requestProducer = requestBuilder.build();

        if(context.getConfig().getHttpVersion() == 1.1) {
            clientBuilder.setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1);
        }else if (context.getConfig().getHttpVersion() == 2.0){
            clientBuilder.setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2);
        }else {
            clientBuilder.setVersionPolicy(HttpVersionPolicy.NEGOTIATE);
        }
        CloseableHttpAsyncClient client = clientBuilder.build();


        byte[] bytes;
        SimpleHttpResponse httpResponse = null;

        AbstractCharResponseConsumer<SimpleHttpResponse> responseConsumer = new AbstractCharResponseConsumer<SimpleHttpResponse>() {
            SimpleHttpResponse httpResponse;
            String body = new String();
            @Override
            protected void start(org.apache.hc.core5.http.HttpResponse response, ContentType contentType) throws HttpException, IOException {
                httpResponse = SimpleHttpResponse.copy(response);
            }

            @Override
            protected SimpleHttpResponse buildResult() throws IOException {
                httpResponse.setBodyText(body, httpResponse.getContentType());
                return httpResponse;
            }

            @Override
            public SimpleHttpResponse getResult() {
                return httpResponse;
            }

            @Override
            protected int capacity() {
                return Integer.MAX_VALUE;
            }

            @Override
            protected void data(CharBuffer charBuffer, boolean b) throws IOException {

                if(!b){
                    body += charBuffer.toString();

                }

            }

            @Override
            public void releaseResources() {

            }
        };

        Future<SimpleHttpResponse> future;
        try {
            client.start();
            future =  client.execute(requestProducer, responseConsumer, new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(SimpleHttpResponse simpleHttpResponse) {

                        }

                        @Override
                        public void failed(Exception e) {

                        }

                        @Override
                        public void cancelled() {

                        }
                    });
            SimpleHttpResponse res =  future.get();

            if(responseConsumer == null || responseConsumer.getResult() == null) {
                bytes = new byte[0];
            }else {
                httpResponse = responseConsumer.getResult();
                InputStream is = new ByteArrayInputStream(httpResponse.getBodyBytes());
                bytes = FileUtils.toBytes(is);
            }


            /*HttpEntity responseEntity = httpResponse.getEntity();
            if (responseEntity == null || responseEntity.getContent() == null) {
                bytes = new byte[0];
            } else {
                InputStream is = responseEntity.getContent();
                bytes = FileUtils.toBytes(is);
            }*/
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        HttpRequest actualRequest = context.getPrevRequest();
        HttpResponse response = new HttpResponse(actualRequest.getStartTime(), actualRequest.getEndTime());
        response.setUri(getRequestUri());
        response.setBody(bytes);
        response.setStatus(httpResponse.getCode());
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
        client.shutdown(ShutdownType.GRACEFUL);
        return response;
    }

    @Override
    protected String getRequestUri() {

        return requestBuilder.getUri().toString();
    }

}
