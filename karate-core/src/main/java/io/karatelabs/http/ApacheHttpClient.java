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

import io.karatelabs.output.HttpLogger;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.brotli.dec.BrotliInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.karatelabs.common.Resource;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApacheHttpClient implements HttpClient, HttpRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheHttpClient.class);

    private HttpRequest request;
    private CloseableHttpClient httpClient;
    private BasicCookieStore cookieStore;
    private volatile ClassicHttpRequest currentRequest;

    private int readTimeout = 30000;
    private int connectTimeout = 30000;

    private boolean followRedirects = true;

    private String proxyUri;
    private String proxyUsername;
    private String proxyPassword;
    private List<String> nonProxyHosts;

    private boolean ssl = false;
    private String sslAlgorithm = "TLS";
    private String sslKeyStore;
    private String sslKeyStorePassword;
    private String sslKeyStoreType;
    private String sslTrustStore;
    private String sslTrustStorePassword;
    private String sslTrustStoreType;
    private boolean sslTrustAll = true;

    private boolean httpRetryEnabled = false;
    private int retryCount = 3;
    private int retryInterval = 1000;

    private Charset charset = StandardCharsets.UTF_8;
    private InetAddress localAddress;

    private String ntlmUsername;
    private String ntlmPassword;
    private String ntlmDomain;
    private String ntlmWorkstation;

    private final HttpLogger logger = new HttpLogger();

    @SuppressWarnings("unchecked")
    @Override
    public void config(String key, Object value) {
        switch (key) {
            case "ssl":
                if (value instanceof Boolean flag) {
                    ssl = flag;
                } else if (value instanceof Map) {
                    ssl = true;
                    Map<String, Object> map = (Map<String, Object>) value;
                    sslKeyStore = (String) map.get("keyStore");
                    sslKeyStorePassword = (String) map.get("keyStorePassword");
                    sslKeyStoreType = (String) map.get("keyStoreType");
                    sslTrustStore = (String) map.get("trustStore");
                    sslTrustStorePassword = (String) map.get("trustStorePassword");
                    sslTrustStoreType = (String) map.get("trustStoreType");
                    Boolean trustAll = (Boolean) map.get("trustAll");
                    if (trustAll != null) {
                        sslTrustAll = trustAll;
                    }
                    String algorithm = (String) map.get("algorithm");
                    if (algorithm != null) {
                        sslAlgorithm = algorithm;
                    }
                } else {
                    LOGGER.warn("boolean or object expected for: {}", key);
                }
                break;
            case "proxy":
                switch (value) {
                    case null -> proxyUri = null;
                    case String s -> proxyUri = s;
                    case Map<?, ?> ignored -> {
                        Map<String, Object> map = (Map<String, Object>) value;
                        proxyUri = (String) map.get("uri");
                        proxyUsername = (String) map.get("username");
                        proxyPassword = (String) map.get("password");
                        nonProxyHosts = (List<String>) map.get("nonProxyHosts");
                    }
                    default -> LOGGER.warn("string or object expected for: {}", key);
                }
                break;
            case "readTimeout":
                if (value instanceof Number time) {
                    readTimeout = time.intValue();
                } else {
                    LOGGER.warn("number expected for: {}", key);
                }
                break;
            case "connectTimeout":
                if (value instanceof Number time) {
                    connectTimeout = time.intValue();
                } else {
                    LOGGER.warn("number expected for: {}", key);
                }
                break;
            case "followRedirects":
                if (value instanceof Boolean flag) {
                    followRedirects = flag;
                } else {
                    LOGGER.warn("boolean expected for: {}", key);
                }
                break;
            case "httpRetryEnabled":
                if (value instanceof Boolean flag) {
                    httpRetryEnabled = flag;
                } else {
                    LOGGER.warn("boolean expected for: {}", key);
                }
                break;
            case "retry":
                if (value instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) value;
                    if (map.get("count") instanceof Number count) {
                        retryCount = count.intValue();
                    }
                    if (map.get("interval") instanceof Number interval) {
                        retryInterval = interval.intValue();
                    }
                }
                break;
            case "localAddress":
                if (value instanceof String s) {
                    try {
                        localAddress = InetAddress.getByName(s);
                    } catch (Exception e) {
                        LOGGER.warn("invalid local address: {}", s);
                    }
                } else if (value == null) {
                    localAddress = null;
                } else {
                    LOGGER.warn("string expected for: {}", key);
                }
                break;
            case "charset":
                if (value instanceof String s) {
                    charset = Charset.forName(s);
                } else if (value == null) {
                    charset = StandardCharsets.UTF_8;
                } else {
                    LOGGER.warn("string expected for: {}", key);
                }
                break;
            case "auth":
                if (value == null) {
                    // Clear NTLM config if it was auth type ntlm
                    ntlmUsername = null;
                    ntlmPassword = null;
                    ntlmDomain = null;
                    ntlmWorkstation = null;
                } else if (value instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) value;
                    String type = (String) map.get("type");
                    if ("ntlm".equals(type)) {
                        LOGGER.warn("NTLM auth is deprecated in HttpClient 5, consider using Basic or Bearer auth with TLS");
                        ntlmUsername = (String) map.get("username");
                        ntlmPassword = (String) map.get("password");
                        ntlmDomain = (String) map.get("domain");
                        ntlmWorkstation = (String) map.get("workstation");
                    }
                    // Other auth types (basic, bearer, oauth2) are handled at the request level
                }
                break;
            case "ntlmAuth":
                // Legacy support - convert to auth with type: 'ntlm'
                if (value == null) {
                    ntlmUsername = null;
                    ntlmPassword = null;
                    ntlmDomain = null;
                    ntlmWorkstation = null;
                } else if (value instanceof Map) {
                    LOGGER.warn("ntlmAuth is deprecated in HttpClient 5, consider using Basic or Bearer auth with TLS");
                    Map<String, Object> map = (Map<String, Object>) value;
                    ntlmUsername = (String) map.get("username");
                    ntlmPassword = (String) map.get("password");
                    ntlmDomain = (String) map.get("domain");
                    ntlmWorkstation = (String) map.get("workstation");
                } else {
                    LOGGER.warn("map expected for: {}", key);
                }
                break;
            default:
                LOGGER.warn("unexpected key: {}", key);
        }
        LOGGER.debug("http client configured: {}", key);
        httpClient = null; // will force lazy rebuild
    }

    @SuppressWarnings("deprecation")
    private void initHttpClient() {
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.useSystemProperties();
        clientBuilder.disableContentCompression(); // handle brotli manually with pure-java library
        if (httpRetryEnabled) {
            clientBuilder.setRetryStrategy(new DefaultHttpRequestRetryStrategy(
                    retryCount,
                    TimeValue.ofMilliseconds(retryInterval)
            ));
        } else {
            clientBuilder.disableAutomaticRetries();
        }
        if (followRedirects) {
            clientBuilder.setRedirectStrategy(DefaultRedirectStrategy.INSTANCE);
        } else {
            clientBuilder.disableRedirectHandling();
        }
        cookieStore = new BasicCookieStore();
        clientBuilder.setDefaultCookieStore(cookieStore);
        if (ssl) {
            KeyStore trustStore = getKeyStore(sslTrustStore, sslTrustStorePassword, sslTrustStoreType);
            KeyStore keyStore = getKeyStore(sslKeyStore, sslKeyStorePassword, sslKeyStoreType);
            SSLContext sslContext;
            try {
                SSLContextBuilder builder = SSLContexts.custom().setProtocol(sslAlgorithm); // will default to TLS if null
                if (trustStore == null && sslTrustAll) {
                    builder = builder.loadTrustMaterial(new TrustAllStrategy());
                } else {
                    if (sslTrustAll) {
                        builder = builder.loadTrustMaterial(trustStore, new TrustSelfSignedStrategy());
                    } else {
                        builder = builder.loadTrustMaterial(trustStore, null); // will use system / java default
                    }
                }
                if (keyStore != null) {
                    char[] keyPassword = sslKeyStorePassword == null ? null : sslKeyStorePassword.toCharArray();
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
                LOGGER.error("ssl context init failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        } else {
            try {
                SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(null, (chain, authType) -> true).build();
                SSLConnectionSocketFactory socketFactory = new LenientSslConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
                connectionManagerBuilder.setSSLSocketFactory(socketFactory);
            } catch (Exception e) {
                LOGGER.error("ssl context init failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        connectionManagerBuilder
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setSocketTimeout(readTimeout, TimeUnit.MILLISECONDS)
                        .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(connectTimeout, TimeUnit.MILLISECONDS).build());
        RequestConfig.Builder configBuilder = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT);
        // Configure NTLM authentication (deprecated in HttpClient 5)
        if (ntlmUsername != null) {
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            @SuppressWarnings("deprecation")
            NTCredentials ntCredentials = new NTCredentials(
                    ntlmUsername,
                    ntlmPassword != null ? ntlmPassword.toCharArray() : null,
                    ntlmWorkstation,
                    ntlmDomain
            );
            credsProvider.setCredentials(new AuthScope(null, -1), ntCredentials);
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
        }
        // Configure local address binding via custom RoutePlanner
        if (localAddress != null) {
            final InetAddress addr = localAddress;
            clientBuilder.setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE) {
                @Override
                protected InetAddress determineLocalAddress(HttpHost firstHop, HttpContext context) {
                    return addr;
                }
            });
        }
        clientBuilder
                .setDefaultRequestConfig(configBuilder.build())
                .setConnectionManager(connectionManagerBuilder.build())
                .addRequestInterceptorLast(this);
        httpClient = clientBuilder.build();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("http client created");
        }
    }

    @Override
    public HttpResponse invoke(HttpRequest request) {
        this.request = request;
        try {
            ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.create(request.getMethod()).setUri(request.getUrlAndPath());
            if (request.getBody() != null) {
                EntityBuilder entityBuilder = EntityBuilder.create().setBinary(request.getBody());
                // Apply charset to content type
                String contentTypeHeader = request.getHeader(HttpUtils.Header.CONTENT_TYPE.key);
                if (contentTypeHeader != null) {
                    try {
                        ContentType parsed = ContentType.parse(contentTypeHeader);
                        // Use configured charset if not already specified in content-type
                        if (parsed.getCharset() == null) {
                            entityBuilder.setContentType(ContentType.create(parsed.getMimeType(), charset));
                        } else {
                            entityBuilder.setContentType(parsed);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("could not parse content-type: {}", contentTypeHeader);
                    }
                }
                List<String> transferEncoding = request.getHeaderValues(HttpUtils.Header.TRANSFER_ENCODING.key);
                if (transferEncoding != null) {
                    for (String te : transferEncoding) {
                        if (te == null) {
                            continue;
                        }
                        if (te.contains("chunked")) { // can be comma delimited as per spec
                            entityBuilder.chunked();
                        }
                        if (te.contains("gzip")) {
                            entityBuilder.setContentEncoding("gzip");
                        }
                    }
                    request.removeHeader(HttpUtils.Header.TRANSFER_ENCODING.key);
                }
                requestBuilder.setEntity(entityBuilder.build());
            }
            if (request.getHeaders() != null) {
                request.getHeaders().forEach((k, vals) -> vals.forEach(v -> requestBuilder.addHeader(k, v)));
            }
            if (httpClient == null) {
                initHttpClient();
            }
            currentRequest = requestBuilder.build();
            HttpResponse finalResponse = httpClient.execute(currentRequest, response -> buildResponse(response, startTime));
            currentRequest = null; // clear after completion
            // Merge cookies from the store (captured during redirects) with response headers
            mergeCookiesFromStore(finalResponse);
            finalResponse.setRequest(request);
            logger.logResponse(finalResponse);
            return finalResponse;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            currentRequest = null;
        }
    }

    @Override
    public void abort() {
        ClassicHttpRequest req = currentRequest;
        if (req instanceof HttpUriRequestBase) {
            ((HttpUriRequestBase) req).abort();
            LOGGER.warn("http request aborted");
        }
    }

    private long startTime;

    @Override
    public void process(org.apache.hc.core5.http.HttpRequest hr, EntityDetails entity, HttpContext context) {
        request.setHeaders(toHeaders(hr));
        logger.logRequest(request);
        startTime = System.currentTimeMillis();
    }

    static HttpResponse buildResponse(org.apache.hc.core5.http.HttpResponse httpResponse, long startTime) {
        long endTime = System.currentTimeMillis();
        int statusCode = httpResponse.getCode();
        Map<String, List<String>> headers = toHeaders(httpResponse);
        HttpResponse response = new HttpResponse();
        response.setStartTime(startTime);
        response.setResponseTime(endTime - startTime);
        response.setStatus(statusCode);
        response.setHeaders(headers);
        if (httpResponse instanceof ClassicHttpResponse classicHttpResponse) {
            HttpEntity entity = classicHttpResponse.getEntity();
            if (entity != null) {
                try {
                    byte[] bytes;
                    Header contentEncoding = httpResponse.getFirstHeader("Content-Encoding");
                    String encoding = contentEncoding != null ? contentEncoding.getValue() : null;
                    if ("br".equalsIgnoreCase(encoding)) {
                        try (InputStream is = entity.getContent();
                             BrotliInputStream brotliIs = new BrotliInputStream(is)) {
                            bytes = brotliIs.readAllBytes();
                        }
                    } else if ("gzip".equalsIgnoreCase(encoding)) {
                        try (InputStream is = entity.getContent();
                             java.util.zip.GZIPInputStream gzipIs = new java.util.zip.GZIPInputStream(is)) {
                            bytes = gzipIs.readAllBytes();
                        }
                    } else if ("deflate".equalsIgnoreCase(encoding)) {
                        try (InputStream is = entity.getContent();
                             java.util.zip.InflaterInputStream inflaterIs = new java.util.zip.InflaterInputStream(is)) {
                            bytes = inflaterIs.readAllBytes();
                        }
                    } else {
                        bytes = EntityUtils.toByteArray(entity);
                    }
                    response.setBody(bytes, null);
                    response.setContentLength(bytes.length);
                } catch (Exception e) {
                    LOGGER.warn("error extracting response body: {}", e.getMessage());
                }
            }
        }
        return response;
    }

    /**
     * Merge cookies from Apache's cookie store (captured during redirects) with response headers.
     * This ensures cookies set during redirects are visible to Karate's cookie management.
     * After merging, the cookie store is cleared for the next request.
     */
    private void mergeCookiesFromStore(HttpResponse response) {
        if (cookieStore == null) {
            return;
        }
        List<Cookie> storedCookies = cookieStore.getCookies();
        Map<String, List<String>> headers = response.getHeaders();
        if (headers == null) {
            headers = new LinkedHashMap<>();
            response.setHeaders(headers);
        }
        List<String> responseCookies = headers.get(HttpUtils.Header.SET_COOKIE.key);
        if (responseCookies == null) {
            responseCookies = new ArrayList<>();
        }
        // Track cookie names already in response to avoid duplicates
        Set<String> alreadyMerged = new HashSet<>();
        for (String cookieValue : responseCookies) {
            try {
                io.netty.handler.codec.http.cookie.Cookie c = ClientCookieDecoder.LAX.decode(cookieValue);
                if (c != null) {
                    alreadyMerged.add(c.name());
                }
            } catch (Exception e) {
                LOGGER.debug("could not decode cookie: {}", cookieValue);
            }
        }
        // Add cookies from store that aren't already in response
        List<String> mergedCookies = new ArrayList<>(responseCookies);
        for (Cookie c : storedCookies) {
            String name = c.getName();
            if (!alreadyMerged.contains(name)) {
                // Convert Apache Cookie to Set-Cookie header format
                Map<String, Object> map = new LinkedHashMap<>();
                map.put(Cookies.NAME, name);
                map.put(Cookies.VALUE, c.getValue());
                if (c.getDomain() != null) {
                    map.put(Cookies.DOMAIN, c.getDomain());
                }
                if (c.getPath() != null) {
                    map.put(Cookies.PATH, c.getPath());
                }
                if (c.getExpiryInstant() != null) {
                    map.put(Cookies.MAX_AGE, c.getExpiryInstant().toEpochMilli());
                }
                map.put(Cookies.SECURE, c.isSecure());
                io.netty.handler.codec.http.cookie.Cookie nettyCookie = Cookies.fromMap(map);
                String cookieValue = ServerCookieEncoder.LAX.encode(nettyCookie);
                mergedCookies.add(cookieValue);
                alreadyMerged.add(name);
            }
        }
        if (!mergedCookies.isEmpty()) {
            headers.put(HttpUtils.Header.SET_COOKIE.key, mergedCookies);
        }
        // Clear cookie store for next request - Karate manages cookies at a higher level
        cookieStore.clear();
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

    private static KeyStore getKeyStore(String keyStorePath, String password, String type) {
        if (keyStorePath == null) {
            return null;
        }
        char[] passwordChars = password == null ? null : password.toCharArray();
        if (type == null) {
            type = KeyStore.getDefaultType();
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            Resource resource = Resource.path(keyStorePath);
            byte[] bytes = resource.getStream().readAllBytes();
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                keyStore.load(is, passwordChars);
            }
            LOGGER.debug("key store key count for {}: {}", keyStorePath, keyStore.size());
            return keyStore;
        } catch (Exception e) {
            LOGGER.error("key store init failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    static class LenientSslConnectionSocketFactory extends SSLConnectionSocketFactory {

        LenientSslConnectionSocketFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier) {
            super(sslContext, hostnameVerifier);
        }

        @Override
        public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context) throws IOException {
            return super.createLayeredSocket(socket, "", port, context);
        }

    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            try {
                httpClient.close();
                LOGGER.debug("http client closed");
            } finally {
                httpClient = null;
            }
        }
    }

}
