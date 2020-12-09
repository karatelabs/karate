package com.intuit.karate.job;

import com.intuit.karate.http.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class JobManagerRunner {

    static final Logger logger = LoggerFactory.getLogger(JobManagerRunner.class);

    @Test
    void testServer() {
        JobManager jm = new JobManager();
        HttpServer server = new HttpServer(8080, jm);
        server.waitSync();
    }

}
