package com.intuit.karate.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.ScenarioEngine;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class HttpClientTester {

    static final Logger logger = LoggerFactory.getLogger(HttpClientTester.class);

    @Test
    void testGet() {
        ScenarioEngine scenarioEngine = ScenarioEngine.forTempUse();
        ArmeriaHttpClient client = new ArmeriaHttpClient(scenarioEngine.getConfig(), scenarioEngine.logger);
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/users/1").header("Accept", "application/json").invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

    @Test
    void testPostChunked() {
        ApacheHttpClient client = new ApacheHttpClient(ScenarioEngine.forTempUse());
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/posts").
                header("Content-Type", "application/json").
                header("Transfer-Encoding", "chunked").
                method("post").
                bodyJson("{\"title\":\"foo\",\"body\":\"bar\",\"userId\":1}").
                invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

    @Test
    void testPostGzipped() {
        ApacheHttpClient client = new ApacheHttpClient(ScenarioEngine.forTempUse());
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/posts").
                header("Content-Type", "application/json").
                header("Transfer-Encoding", "gzip").
                method("post").
                bodyJson("{\"title\":\"foo\",\"body\":\"bar\",\"userId\":1}").
                invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

    @Test
    void testPostGzippedAndChunked() {
        ApacheHttpClient client = new ApacheHttpClient(ScenarioEngine.forTempUse());
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/posts").
                header("Content-Type", "application/json").
                header("Transfer-Encoding", "gzip").
                header("Transfer-Encoding", "chunked").
                method("post").
                bodyJson("{\"title\":\"foo\",\"body\":\"bar\",\"userId\":1}").
                invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

    @Test
    void testPostGzippedAndChunkedSingleHeader() {
        ApacheHttpClient client = new ApacheHttpClient(ScenarioEngine.forTempUse());
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/posts").
                header("Content-Type", "application/json").
                header("Transfer-Encoding", "gzip, chunked").
                method("post").
                bodyJson("{\"title\":\"foo\",\"body\":\"bar\",\"userId\":1}").
                invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

}
