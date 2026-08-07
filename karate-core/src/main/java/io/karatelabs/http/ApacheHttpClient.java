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
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
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
import io.karatelabs.core.KarateConfig;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ApacheHttpClient implements HttpClient, HttpRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheHttpClient.class);

    private HttpRequest request;
    // Volatile because a client can legitimately be reached from a thread other than the one
    // that built it: an afterFeature hook runs on the feature thread after the scenario lanes
    // join, and a closure returned from a called feature can be invoked later. That path happens
    // to be safe already — it goes through FeatureRuntime.lastExecuted, which is volatile — but
    // resting this field's correctness on a different field's volatility is not a property worth
    // keeping. It costs nothing here: the field is read once per request.
    private volatile CloseableHttpClient httpClient;
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

    /**
     * The config this client was last built from, or null before the first {@link #apply}.
     *
     * <p>Volatile for the same reason {@link #httpClient} is: it is read once per apply and the
     * cost is nothing, and resting its correctness on the caller always being the owning scenario
     * thread is not a property worth keeping.
     */
    private volatile ClientKey currentKey;

    /**
     * Everything about a {@link KarateConfig} that changes the client this class builds — and
     * nothing else. Equal keys mean an identical client, so {@link #apply} can return without
     * tearing the transport down.
     *
     * <p><b>When in doubt, include the field.</b> An extra component costs one redundant rebuild;
     * a missing one leaves a scenario running against a client that no longer matches its own
     * config, silently. {@code charset} is deliberately coarse on those grounds — compared raw
     * although {@code apply()} ignores a null one — and only NTLM is taken from auth, because the
     * rest of auth never reaches the built client.
     *
     * <p><b>Read off the raw config, never off this class's resolved fields</b>, or deciding
     * whether to resolve {@code localAddress} would first resolve it: {@code apply()} puts that
     * through {@code getByName}, which can hit DNS.
     *
     * <p><b>Config only — the environment is not keyed.</b> The build also reads things no
     * {@code KarateConfig} setting describes: the bytes behind {@code sslKeyStore}, and the JVM
     * proxy properties {@code useSystemProperties()} picks up. Re-applying an identical config
     * used to re-read those; now it does not, so a certificate rotated on disk mid-run cannot be
     * picked up from a feature. Change a keyed setting, or take a new client.
     */
    private record ClientKey(
            boolean ssl, String sslAlgorithm, String sslKeyStore, String sslKeyStorePassword,
            String sslKeyStoreType, String sslTrustStore, String sslTrustStorePassword,
            String sslTrustStoreType, boolean sslTrustAll,
            String proxyUri, String proxyUsername, String proxyPassword, List<String> nonProxyHosts,
            int readTimeout, int connectTimeout, boolean followRedirects,
            boolean httpRetryEnabled, int retryCount, int retryInterval,
            Charset charset, String localAddress,
            String ntlmUsername, String ntlmPassword, String ntlmDomain, String ntlmWorkstation) {

        static ClientKey of(KarateConfig config) {
            // getNonProxyHosts() hands back the live List inside the proxy map rather than a copy,
            // so retaining the reference would compare that list against itself after an in-place
            // edit and conclude nothing changed.
            //
            // ArrayList rather than List.copyOf: the list comes from a feature file, so
            // `configure proxy = { nonProxyHosts: ['a', null] }` is reachable, and List.copyOf
            // rejects null elements. Failing apply() on a config the client would otherwise have
            // accepted is a worse outcome than carrying a null through a comparison.
            List<String> nonProxy = config.getNonProxyHosts();
            return new ClientKey(
                    config.isSslEnabled(), config.getSslAlgorithm(), config.getSslKeyStore(),
                    config.getSslKeyStorePassword(), config.getSslKeyStoreType(),
                    config.getSslTrustStore(), config.getSslTrustStorePassword(),
                    config.getSslTrustStoreType(), config.isSslTrustAll(),
                    config.getProxyUri(), config.getProxyUsername(), config.getProxyPassword(),
                    nonProxy == null ? null : new ArrayList<>(nonProxy),
                    config.getReadTimeout(), config.getConnectTimeout(), config.isFollowRedirects(),
                    // The count and interval reach the client only through the retry strategy,
                    // which is only installed when retry is enabled. Keying them unconditionally
                    // would tear down a live client every time a callee set `configure retry` to
                    // tune its own `retry until` polling — a common pattern that has nothing to do
                    // with the transport, and one that now reaches apply() on every key.
                    config.isHttpRetryEnabled(),
                    config.isHttpRetryEnabled() ? config.getRetryCount() : 0,
                    config.isHttpRetryEnabled() ? config.getRetryInterval() : 0,
                    config.getCharset(), config.getLocalAddress(),
                    config.getNtlmUsername(), config.getNtlmPassword(),
                    config.getNtlmDomain(), config.getNtlmWorkstation());
        }
    }

    @Override
    public void apply(KarateConfig config) {
        if (config == null) return;
        // Nothing that reaches the wire changed, so the built client is still the right one.
        //
        // This guard is the ONLY thing deciding whether a rebuild is needed. Callers used to make
        // that call themselves, from a hand-maintained per-key verdict in KarateConfig.configure,
        // which could answer only for `configure <key>` steps — the three paths that copy a whole
        // config across a scenario boundary (a shared-scope call returning, a callee inheriting
        // from its caller, a cached callonce replaying) have no key to consult and so rebuilt
        // unconditionally. A scenario doing request -> shared-scope call -> request therefore
        // destroyed a live client, its connection manager and its sockets, having changed nothing.
        ClientKey key = ClientKey.of(config);
        if (key.equals(currentKey)) {
            LOGGER.trace("http client config unchanged, keeping the built client");
            return;
        }
        // SSL
        ssl = config.isSslEnabled();
        sslAlgorithm = config.getSslAlgorithm();
        sslKeyStore = config.getSslKeyStore();
        sslKeyStorePassword = config.getSslKeyStorePassword();
        sslKeyStoreType = config.getSslKeyStoreType();
        sslTrustStore = config.getSslTrustStore();
        sslTrustStorePassword = config.getSslTrustStorePassword();
        sslTrustStoreType = config.getSslTrustStoreType();
        sslTrustAll = config.isSslTrustAll();
        // Proxy
        proxyUri = config.getProxyUri();
        proxyUsername = config.getProxyUsername();
        proxyPassword = config.getProxyPassword();
        nonProxyHosts = config.getNonProxyHosts();
        // Timeouts / redirects
        readTimeout = config.getReadTimeout();
        connectTimeout = config.getConnectTimeout();
        followRedirects = config.isFollowRedirects();
        // Retry
        httpRetryEnabled = config.isHttpRetryEnabled();
        retryCount = config.getRetryCount();
        retryInterval = config.getRetryInterval();
        // Charset
        if (config.getCharset() != null) {
            charset = config.getCharset();
        }
        // Local address
        String localAddr = config.getLocalAddress();
        boolean unresolved = false;
        if (localAddr != null) {
            try {
                localAddress = InetAddress.getByName(localAddr);
            } catch (Exception e) {
                // The field keeps whatever it resolved to last, so this config was NOT fully
                // applied — see where the key is committed.
                unresolved = true;
                LOGGER.warn("invalid local address: {}", localAddr);
            }
        } else {
            localAddress = null;
        }
        // NTLM (auth.type=ntlm)
        if ("ntlm".equals(config.getAuthType())) {
            ntlmUsername = config.getNtlmUsername();
            ntlmPassword = config.getNtlmPassword();
            ntlmDomain = config.getNtlmDomain();
            ntlmWorkstation = config.getNtlmWorkstation();
        } else {
            ntlmUsername = null;
            ntlmPassword = null;
            ntlmDomain = null;
            ntlmWorkstation = null;
        }
        // Committed only now that every field above took, and only if they all did.
        //
        // A name that would not resolve is the one way to get here having NOT applied the config:
        // the exception is caught, so localAddress still holds the previous address. Committing
        // the key anyway would make a transient DNS failure permanent — every later apply() of
        // that same config would match and skip, leaving the client bound to the wrong interface
        // for the rest of the scenario with one WARN as the only trace. Clearing it instead makes
        // the next apply() retry the lookup, which is what happened before this guard existed.
        currentKey = unresolved ? null : key;
        // Close the outgoing one before dropping it. Nulling alone abandons a built
        // CloseableHttpClient, its connection manager and its pooled sockets to the collector,
        // which is the same nondeterministic release that closing at scenario end was meant to
        // remove. Reaching here now means the config genuinely changed, so this teardown is work
        // that had to happen either way.
        closeQuietly();
        LOGGER.debug("http client config applied");
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
        // A pooling factory supplies one connection manager for the whole run and hands each
        // scenario its own thin wrapper over it. setConnectionManagerShared(true) is what makes
        // that safe: without it, closing any one wrapper at scenario end would shut the shared
        // manager and every other scenario with it.
        //
        // What a shared manager cannot carry: the SSL socket factory and the socket/connect
        // timeouts are configured on the manager, so per-scenario `configure ssl` / `configure
        // connectTimeout` do not apply to a pooled client. That is the trade a pooling factory
        // makes, and it is why this is opt-in rather than the default.
        org.apache.hc.client5.http.io.HttpClientConnectionManager shared = sharedConnectionManager();
        if (shared != null) {
            clientBuilder.setConnectionManager(shared).setConnectionManagerShared(true);
        } else {
            clientBuilder.setConnectionManager(connectionManagerBuilder.build());
        }
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
        // Configure proxy
        if (proxyUri != null) {
            try {
                URI proxy = new URI(proxyUri);
                HttpHost proxyHost = new HttpHost(proxy.getScheme(), proxy.getHost(), proxy.getPort());
                if (proxyUsername != null && proxyPassword != null) {
                    BasicCredentialsProvider proxyCredsProvider = new BasicCredentialsProvider();
                    proxyCredsProvider.setCredentials(
                            new AuthScope(proxy.getHost(), proxy.getPort()),
                            new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(
                                    proxyUsername, proxyPassword.toCharArray()));
                    clientBuilder.setDefaultCredentialsProvider(proxyCredsProvider);
                }
                if (nonProxyHosts != null && !nonProxyHosts.isEmpty()) {
                    ProxySelector proxySelector = new ProxySelector() {
                        @Override
                        public List<Proxy> select(URI uri) {
                            String host = uri.getHost();
                            for (String pattern : nonProxyHosts) {
                                if (matchNonProxyHost(pattern, host)) {
                                    return Collections.singletonList(Proxy.NO_PROXY);
                                }
                            }
                            return Collections.singletonList(
                                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                        }

                        @Override
                        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                            LOGGER.info("connect failed to uri: {}", uri, ioe);
                        }
                    };
                    clientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE, proxySelector));
                } else {
                    clientBuilder.setProxy(proxyHost);
                }
            } catch (Exception e) {
                LOGGER.error("proxy config failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
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
                .addRequestInterceptorLast(this);
        httpClient = clientBuilder.build();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("http client created");
        }
    }

    /**
     * Match a host against a non-proxy pattern that may contain a leading or trailing wildcard.
     * Supports: "*.example.com", "192.168.*", "localhost" (exact match).
     */
    static boolean matchNonProxyHost(String pattern, String host) {
        if (pattern == null || host == null) {
            return false;
        }
        if (pattern.startsWith("*")) {
            return host.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*")) {
            return host.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return host.equalsIgnoreCase(pattern);
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
            // A rebuild after release has no owner to hand it back to, so this request owns it
            // and closes it on the way out. That costs a connection setup per post-release
            // request, which is the right trade against leaking one client per occurrence -- and
            // the path is rare by construction: it needs a hook or an escaped closure making a
            // request after its scenario ended.
            boolean orphanRebuild = httpClient == null && released;
            if (httpClient == null) {
                if (released) {
                    LOGGER.debug("http request after the client was released — this request owns "
                            + "its own client and will close it");
                }
                initHttpClient();
            }
            currentRequest = requestBuilder.build();
            HttpResponse finalResponse;
            try {
                finalResponse = httpClient.execute(currentRequest, response -> buildResponse(response, startTime));
            } finally {
                if (orphanRebuild) {
                    closeQuietly();
                }
            }
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
        response.setStatusText(httpResponse.getReasonPhrase());
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
            // pass target host through so SNI carries the real HTTPS target after a proxy CONNECT
            return super.createLayeredSocket(socket, target, port, context);
        }

    }

    /**
     * The connection manager this client should use, or null to build a private one per instance
     * — which is the default and what every functional test wants.
     *
     * <p>Override to share one across scenarios. See {@code karate-profiling}'s
     * {@code PooledHttpClientFactory} for the shape: one manager per run, one subclass instance
     * per scenario. Do not return a manager from a factory that hands the same
     * {@code ApacheHttpClient} instance to concurrent scenarios — that is unsafe for reasons
     * unrelated to pooling, see {@link HttpClientFactory#release}.
     */
    protected org.apache.hc.client5.http.io.HttpClientConnectionManager sharedConnectionManager() {
        return null;
    }

    @Override
    public void close() throws IOException {
        // Remember that the owner has handed this instance back. Not to forbid later use -- an
        // afterFeature hook, or a closure returned from a called feature, legitimately outlives
        // the scenario that released the client -- but so that a client rebuilt after this point
        // does not become an orphan. See invoke().
        released = true;
        closeQuietly();
    }

    /**
     * Set by {@link #close()}. From then on the owning scenario is gone, so nobody will call
     * {@code release()} again and any client built after this point would be abandoned: its
     * connection manager and pooled sockets released whenever the collector got to them, which is
     * the exact leak the release contract was added to remove.
     */
    private volatile boolean released;

    /**
     * Close the current client, if any, and forget it.
     *
     * <p>Note what {@code invoke()} does afterwards: it sees a null client and lazily builds a new
     * one. That is deliberate — {@code apply()} relies on it to rebuild after a configuration
     * change — and it also means an instance stays usable after {@link #close()}, which a hook or
     * an escaped closure relies on. {@code invoke()} handles the difference: a rebuild that
     * happens after release is owned by that one request and closed when it finishes, so it
     * cannot become an orphan. See the contract note on {@code HttpClientFactory.release}.
     */
    private void closeQuietly() {
        if (httpClient != null) {
            try {
                httpClient.close();
                LOGGER.debug("http client closed");
            } catch (Exception e) {
                LOGGER.warn("error closing http client: {}", e.getMessage());
            } finally {
                httpClient = null;
            }
        }
    }

}
