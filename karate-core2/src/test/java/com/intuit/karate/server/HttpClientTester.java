package com.intuit.karate.server;

import com.intuit.karate.FileUtils;
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
        ArmeriaHttpClient client = new ArmeriaHttpClient(null);
        HttpRequestBuilder http = new HttpRequestBuilder(client);
        Response response = http.url("https://jsonplaceholder.typicode.com/users").invoke();
        String body = FileUtils.toString(response.getBody());
        logger.debug("response: {}", body);
    }

}
