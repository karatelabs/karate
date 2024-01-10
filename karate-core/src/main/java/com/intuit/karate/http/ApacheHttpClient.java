/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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

import com.intuit.karate.Constants;
import com.intuit.karate.Logger;
import com.intuit.karate.core.Config;
import com.intuit.karate.core.ScenarioEngine;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.cookie.CookieSpecBase;
import org.apache.hc.client5.http.impl.cookie.RFC6265StrictSpec;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.LenientSslConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 *
 * @author pthomas3
 */
public class ApacheHttpClient implements HttpClient, HttpRequestInterceptor {

    private final ScenarioEngine engine;
    private final Logger logger;
    private final HttpLogger httpLogger;

    private HttpClientBuilder clientBuilder;
    private CookieStore cookieStore;

    // Not sure what the rationale was behind this class.
    // But the httpclient4 ApacheHttpClient, based on DefaultCookieSpec, supported:
    // - set-cookie2 which is now deprecated https://stackoverflow.com/questions/9462180/difference-between-set-cookie2-and-set-cookie
    // - "netscape style cookies" and versioned cookies... whatever that was, I'm asusming its not widely used any more
    // - other than that, it defaulted to a RFC2965Strict Spec.
    // So as part of the httpclient5 migration, we directly default to RFC6265StrictSpec 
    public static class LenientCookieSpec extends CookieSpecBase {

        static final String KARATE = "karate";

        final RFC6265StrictSpec strict = new RFC6265StrictSpec();

        public LenientCookieSpec() {
        }

        @Override
        public boolean match(Cookie cookie, CookieOrigin origin) {
            return true;
        }

        @Override
        public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
            // do nothing
        }


        @Override
        public List<Cookie> parse(Header header, CookieOrigin origin) throws MalformedCookieException {
            return strict.parse(header, origin);
        }

        @Override
        public List<Header> formatCookies(List<Cookie> cookies) {
            return strict.formatCookies(cookies);
        }

        public static Registry<CookieSpecFactory> registry() {
            CookieSpecFactory specProvider = (HttpContext hc) -> new LenientCookieSpec();
            return RegistryBuilder.<CookieSpecFactory>create()
                    .register(KARATE, specProvider).build();
        }
    }

    public ApacheHttpClient(ScenarioEngine engine) {
        this.engine = engine;
        logger = engine.logger;
        httpLogger = new HttpLogger(logger);
        configure(engine.getConfig());
    }

    private void configure(Config config) {
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();                

        clientBuilder = HttpClientBuilder.create();
        
        if (config.isHttpRetryEnabled()) {
            clientBuilder.setRetryStrategy(new CustomHttpRequestRetryHandler(logger));
        } else {
            clientBuilder.disableAutomaticRetries();
        }

        if (!config.isFollowRedirects()) {
            clientBuilder.disableRedirectHandling();
        } else { 
            clientBuilder.setRedirectStrategy(DefaultRedirectStrategy.INSTANCE);
            // httpclient4 was using LaxRedirectStrategy.INSTANCE as it supported redirect on POST methods.
            // httpclient5 seems to be status code based, not method based, so default strategy should be fine.
        }
        cookieStore = new BasicCookieStore();
        clientBuilder.setDefaultCookieStore(cookieStore);
        clientBuilder.setDefaultCookieSpecRegistry(LenientCookieSpec.registry());
        clientBuilder.useSystemProperties();
        if (config.isSslEnabled()) {
            // System.setProperty("jsse.enableSNIExtension", "false");
            String algorithm = config.getSslAlgorithm(); // could be null
            KeyStore trustStore = engine.getKeyStore(config.getSslTrustStore(), config.getSslTrustStorePassword(), config.getSslTrustStoreType());
            KeyStore keyStore = engine.getKeyStore(config.getSslKeyStore(), config.getSslKeyStorePassword(), config.getSslKeyStoreType());
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
                connectionManagerBuilder.setSSLSocketFactory(socketFactory);                
            } catch (Exception e) {
                logger.error("ssl context init failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        connectionManagerBuilder
            .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)        
            .setConnPoolPolicy(PoolReusePolicy.LIFO)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS)
                .setConnectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS).build());   
        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setCookieSpec(LenientCookieSpec.KARATE);                
        if (config.isNtlmEnabled()) { 
            //No longer supported since 5.3. See https://hc.apache.org/httpcomponents-client-5.3.x/current/httpclient5/apidocs/index.html?org/apache/hc/client5/http/auth/NTCredentials.html
            throw new UnsupportedOperationException("NTLM authentication is not supported any more. Please consider using Basic or Bearer authentication with TLS instead.");
        }
        connectionManagerBuilder.setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS).build());

        connManager = connectionManagerBuilder.build();
        clientBuilder.setRoutePlanner(buildRoutePlanner(config))
            .setDefaultRequestConfig(configBuilder.build())
            // set shared flag to true so that we can close the client.
            //ConnectionManager won't be closed automatically by Apache, it is now our responsability to do so.
            // See comments in https://github.com/karatelabs/karate/pull/2471
            .setConnectionManagerShared(true)
            .setConnectionManager(connManager)
            // Not sure about this. With the default reuseStrategy, ProxyServerTest fails with a SocketConnection(client.feature#11).
            // Could not work out the exact reason. But the same SocketHandler was being used for the first two calls and was failing the second time.
            // By setting a no reuse strategy, the connections are closed and the test passes.
            // Impact on performance to be checked.
            .setConnectionReuseStrategy((req, resp, ctx) -> false)
            .addRequestInterceptorLast(this);
    }

    // Differences with httpclient4 implementation:
    // - RequestBuilder.setLocalAddress does not exist any more, so instead, RoutePlanner.determineLocalAddress is overridden
    // - clientBuilder.setProxy is not set any more. I'm probably misreading the code, but looking at DefaultRoutePanner.determineRoute, if proxy exists, my understanding is that determineProxy is not called and therefore proxySelector will NOT be used.
    // so ProxySelector has been redesigned to handle both the specified proxy, and the nonProxyhosts if specified.
    // Note that the route planner must now handle:
    // - localaddress
    // - proxy (set or not)
    // - nonProxy hosts (set or not)
    // Only SystemDefaultRoutePlanner supports proxy/nonProxy host so we subclass that class. However, SystemDefaultRoutePlanner does not have a "no proxy" mode so determineProxy is overridden to opt out if needed. 
    private HttpRoutePlanner buildRoutePlanner(Config config) {
        ProxySelector proxySelector = null;
        if (config.getProxyUri() != null) {
            try {
                URI proxyUri = new URIBuilder(config.getProxyUri()).build();
                
                proxySelector = new ProxySelector() {
                    private final List<String> proxyExceptions = config.getNonProxyHosts() == null ? Collections.emptyList() : config.getNonProxyHosts();

                    @Override
                    public List<Proxy> select(URI uri) {
                        return Collections.singletonList(proxyExceptions.contains(uri.getHost())
                                ? Proxy.NO_PROXY
                                : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())));
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        logger.info("connect failed to uri: {}", uri, ioe);
                    }
                };

                if (config.getProxyUsername() != null && config.getProxyPassword() != null) {
                    BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(
                            new AuthScope(proxyUri.getHost(), proxyUri.getPort()),
                            new UsernamePasswordCredentials(config.getProxyUsername(), config.getProxyPassword().toCharArray()));
                    clientBuilder.setDefaultCredentialsProvider(credsProvider);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        InetAddress localAddress = null;
        if (config.getLocalAddress() != null) {            
            try {
                localAddress = InetAddress.getByName(config.getLocalAddress());
            } catch (Exception e) {
                logger.warn("failed to resolve local address: {} - {}", config.getLocalAddress(), e.getMessage());
            }
        }

        return new CustomRoutePlanner(proxySelector, localAddress);
    }

    @Override
    public void setConfig(Config config) {
        configure(config);
    }

    @Override
    public Config getConfig() {
        return engine.getConfig();
    }

    private HttpRequest request;
    private PoolingHttpClientConnectionManager connManager;

    @Override
    public Response invoke(HttpRequest request) {
        this.request = request;
        ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(request.getMethod()).setUri(request.getUrl());
        if (request.getBody() != null) {
            EntityBuilder entityBuilder = EntityBuilder.create().setBinary(request.getBody());
            List<String> transferEncoding = request.getHeaderValues(HttpConstants.HDR_TRANSFER_ENCODING);
            if (transferEncoding != null) {
                for (String te : transferEncoding) {
                    if (te == null) {
                        continue;
                    }
                    if (te.contains("chunked")) { // can be comma delimited as per spec
                        entityBuilder.chunked();
                    }
                    if (te.contains("gzip")) {
                        entityBuilder.gzipCompressed();
                    }
                }
                request.removeHeader(HttpConstants.HDR_TRANSFER_ENCODING);
            }
            requestBuilder.setEntity(entityBuilder.build());
        }
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((k, vals) -> vals.forEach(v -> requestBuilder.addHeader(k, v)));
        }   
        try (CloseableHttpClient client = clientBuilder.build()) {
            Response response = client.execute(requestBuilder.build(), this::buildResponse);
            request.setEndTime(System.currentTimeMillis());
            httpLogger.logResponse(getConfig(), request, response);
            return response;
        } catch (Exception e) {
            if (e instanceof ClientProtocolException && e.getCause() != null) { // better error message                
                throw new RuntimeException(e.getCause());
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private Response buildResponse(ClassicHttpResponse httpResponse) throws IOException{
        HttpEntity entity = httpResponse.getEntity();
        byte[] bytes = entity != null ? EntityUtils.toByteArray(entity) : Constants.ZERO_BYTES;
        int statusCode = httpResponse.getCode();
        Map<String, List<String>> headers = toHeaders(httpResponse);
        List<Cookie> storedCookies = cookieStore.getCookies();
        Header[] requestCookieHeaders = httpResponse.getHeaders(HttpConstants.HDR_SET_COOKIE);
        // edge case where the apache client
        // auto-followed a redirect where cookies were involved
        List<String> mergedCookieValues = new ArrayList<>(requestCookieHeaders.length);
        Set<String> alreadyMerged = new HashSet<>(requestCookieHeaders.length);
        for (Header ch : requestCookieHeaders) {
            String requestCookieValue = ch.getValue();
            io.netty.handler.codec.http.cookie.Cookie c = ClientCookieDecoder.LAX.decode(requestCookieValue);            
            mergedCookieValues.add(requestCookieValue);
            alreadyMerged.add(c.name());
        }        
        for (Cookie c : storedCookies) {
            if (c.getValue() != null) {
                String name = c.getName();
                if (alreadyMerged.contains(name)) {
                    continue;
                }                
                Map<String, Object> map = new HashMap<>();
                map.put(Cookies.NAME, name);
                map.put(Cookies.VALUE, c.getValue());
                map.put(Cookies.DOMAIN, c.getDomain());
                if (c.getExpiryDate() != null) {
                    map.put(Cookies.MAX_AGE, c.getExpiryDate().getTime());
                }
                map.put(Cookies.SECURE, c.isSecure());
                io.netty.handler.codec.http.cookie.Cookie nettyCookie = Cookies.fromMap(map);
                String cookieValue = ServerCookieEncoder.LAX.encode(nettyCookie);
                mergedCookieValues.add(cookieValue);
            }
        }
        headers.put(HttpConstants.HDR_SET_COOKIE, mergedCookieValues);
        cookieStore.clear();
        Response response = new Response(statusCode, headers, bytes);
        httpLogger.logResponse(getConfig(), request, response);
        return response;
    }

    @Override
    public void process(org.apache.hc.core5.http.HttpRequest hr, EntityDetails entity, HttpContext context) throws HttpException, IOException {
        request.setHeaders(toHeaders(hr));
        httpLogger.logRequest(getConfig(), request);
        request.setStartTime(System.currentTimeMillis());
    }

    private static Map<String, List<String>> toHeaders(HttpMessage msg) {
        Header[] headers = msg.getHeaders();
        Map<String, List<String>> map = new LinkedHashMap<>(headers.length);
        for (Header outer : headers) {
            String name = outer.getName();
            Header[] inner = msg.getHeaders(name);
            List<String> list = new ArrayList<>(inner.length);
            for (Header h : inner) {
                list.add(h.getValue());
            }
            map.put(name, list);
        }
        return map;
    }

    public void close() {
        connManager.close();
    }

    private static class CustomRoutePlanner extends SystemDefaultRoutePlanner {

        private final InetAddress localAddress;
        private ProxySelector proxySelector;

        public CustomRoutePlanner(ProxySelector proxySelector, InetAddress localAddress) {
            super(proxySelector);
            // ProxySelector in SystemDefaultRoutePlanner is private;
            this.proxySelector = proxySelector;
            this.localAddress = localAddress;
        }

          @Override
            protected HttpHost determineProxy(
                final HttpHost target,
                final HttpContext context) throws HttpException {
                if (proxySelector == null) {
                    //SystemDefaultRoutePlanner will default to some system-wide proxySelector.
                    //However, the expected behavior here is to ignore proxy altogether if no selector is supplied.
                    return null; 
                }
                return super.determineProxy(target, context);
            }
    
            protected InetAddress determineLocalAddress(
                    final HttpHost firstHop,
                    final HttpContext context) throws HttpException {
                return localAddress;
            }        



    }
}
