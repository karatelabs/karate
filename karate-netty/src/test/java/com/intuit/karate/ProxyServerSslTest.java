package com.intuit.karate;

import com.google.common.base.Charsets;
import com.intuit.karate.http.LenientTrustManager;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.netty.ProxyServer;
import java.io.File;
import java.io.InputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ProxyServerSslTest {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ProxyServerSslTest.class);

    private static ProxyServer proxy;
    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() {
        proxy = new ProxyServer(0, null, null);
        File file = FileUtils.getFileRelativeTo(ProxyServerSslTest.class, "server.feature");
        server = FeatureServer.start(file, 0, true, null);
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");
        System.setProperty("karate.server.ssl", "true");
        System.setProperty("karate.server.proxy", "http://localhost:" + proxy.getPort());
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
        proxy.stop();
    }

    @Test
    public void testProxy() throws Exception {
        String url = "https://localhost:" + server.getPort() + "/v1/cats";
        assertEquals(200, http(get(url)));
        assertEquals(200, http(post(url, "{ \"name\": \"Billie\" }")));
        Runner.runFeature("classpath:com/intuit/karate/client.feature", null, true);
    }
    
    private static HttpUriRequest get(String url) {
        return new HttpGet(url);
    }

    private static HttpUriRequest post(String url, String body) {
        HttpPost post = new HttpPost(url);
        HttpEntity entity = new StringEntity(body, ContentType.create("application/json", Charsets.UTF_8));
        post.setEntity(entity);
        return post;
    }    

    private int http(HttpUriRequest request) throws Exception {
        // System.setProperty("javax.net.debug", "all"); // -Djavax.net.debug=all
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, new TrustManager[]{LenientTrustManager.INSTANCE}, null);
        CloseableHttpClient client = HttpClients.custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setSSLContext(sc)
                .setProxy(new HttpHost("localhost", proxy.getPort()))
                .build();
        HttpResponse response = client.execute(request);
        InputStream is = response.getEntity().getContent();
        String responseString = FileUtils.toString(is);
        logger.debug("response: {}", responseString);
        return response.getStatusLine().getStatusCode();
    }

}
