package com.intuit.karate.fatjar;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.http.ProxyServer;
import com.intuit.karate.core.MockServer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ProxyServerTest {

    static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerTest.class);

    static ProxyServer proxy;
    static MockServer server;

    @BeforeAll
    static void beforeClass() {
        proxy = new ProxyServer(0, null, null);
        server = MockServer.feature("classpath:com/intuit/karate/fatjar/server.feature").http(0).build();
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
        System.setProperty("karate.server.ssl", ""); // for ci
        System.setProperty("karate.server.proxy", "http://localhost:" + proxy.getPort());
    }

    @AfterAll
    static void afterClass() {
        server.stop();
        proxy.stop();
    }

    @Test
    void testProxy() throws Exception {
        String url = "http://localhost:" + server.getPort() + "/v1/cats";
        assertEquals(200, http(get(url)));
        assertEquals(200, http(post(url, "{ \"name\": \"Billie\" }")));
        Results results = Runner
                .path("classpath:com/intuit/karate/fatjar/client.feature")
                .configDir("classpath:com/intuit/karate/fatjar")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
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

    static int http(HttpUriRequest request) throws Exception {
        CloseableHttpClient client = HttpClients.custom()
                .setProxy(new HttpHost("localhost", proxy.getPort()))
                .build();
        HttpResponse response = client.execute(request);
        InputStream is = response.getEntity().getContent();
        String responseString = FileUtils.toString(is);
        logger.debug("response: {}", responseString);
        return response.getStatusLine().getStatusCode();
    }

}
