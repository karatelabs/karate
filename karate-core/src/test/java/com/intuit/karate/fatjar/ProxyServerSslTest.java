package com.intuit.karate.fatjar;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.http.LenientTrustManager;
import com.intuit.karate.http.ProxyServer;
import com.intuit.karate.core.MockServer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ProxyServerSslTest {

    static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerSslTest.class);

    static ProxyServer proxy;
    static MockServer server;

    @BeforeAll
    static void beforeAll() {
        proxy = new ProxyServer(0, null, null);
        server = MockServer.feature("classpath:com/intuit/karate/fatjar/server.feature").https(0).build();
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
        System.setProperty("karate.server.ssl", "true");
        System.setProperty("karate.server.proxy", "http://localhost:" + proxy.getPort());
    }

    @AfterAll
    static void afterAll() {
        server.stop();
        proxy.stop();
    }

    // @Test
    void testProxy() throws Exception {
        String url = "https://localhost:" + server.getPort() + "/v1/cats";
        assertEquals(200, http(get(url)));
        assertEquals(200, http(post(url, "{ \"name\": \"Billie\" }")));
        Results results = Runner
                .path("classpath:com/intuit/karate/fatjar/client.feature")
                .configDir("classpath:com/intuit/karate/fatjar")
                .parallel(1);
    }

    static HttpUriRequest get(String url) {
        return new HttpGet(url);
    }

    static HttpUriRequest post(String url, String body) {
        HttpPost post = new HttpPost(url);
        HttpEntity entity = new StringEntity(body, ContentType.create("application/json", StandardCharsets.UTF_8));
        post.setEntity(entity);
        return post;
    }

    int http(HttpUriRequest request) throws Exception {
        // System.setProperty("javax.net.debug", "all"); // -Djavax.net.debug=all
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, new TrustManager[]{LenientTrustManager.INSTANCE}, null);
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                .setSslContext(sc)
                                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .build())
                        .build())
                .setProxy(new HttpHost("localhost", proxy.getPort()))
                .build();
        HttpResponse response = client.execute(request);
        String responseString = response.getReasonPhrase();
        logger.debug("response: {}", responseString);
        return response.getCode();
    }

}
