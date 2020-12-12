package com.intuit.karate.job;

import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.Http;
import com.intuit.karate.Json;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.http.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    void testServer() throws Exception {
        Feature feature = Feature.read("classpath:com/intuit/karate/job/test.feature");
        FeatureRuntime fr = FeatureRuntime.of(feature);
        JobConfig jc = new JobConfig() {
            @Override
            public String getHost() {
                return "localhost";
            }

            @Override
            public int getPort() {
                return 8080;
            }

            @Override
            public int getExecutorCount() {
                return 3;
            }

            @Override
            public String getExecutorCommand(String jobId, String jobUrl, int index) {
                return null;
            }

            @Override
            public Map<String, String> getEnvironment() {
                return null;
            }

            @Override
            public List<JobCommand> getStartupCommands() {
                return null;
            }

            @Override
            public List<JobCommand> getMainCommands(JobContext jc) {
                return null;
            }
        };
        JobManager jm = new JobManager(jc, "target/foo");
        final AtomicInteger count = new AtomicInteger();
        new Thread(() -> {
            fr.scenarios.forEachRemaining(sr -> {
                String id = count.incrementAndGet() + "";
                ScenarioJobChunk chunk = new ScenarioJobChunk(id, sr);
                jm.addChunk(chunk);
            });
        }).start();
        Http http = Http.forUrl("http://localhost:8080");
        Json json = Json.of("{ method: 'next', executorId: '1' }");
        json.set("jobId", jm.jobId);
        Response response = http.header(JobMessage.KARATE_JOB, json.toString()).postJson("{}");
        String jobHeader = response.getHeader(JobMessage.KARATE_JOB);
        json = Json.of(jobHeader);
        matchContains(json.asMap(), "{ method: 'next', chunkId: '1' }");
        String chunkId = json.get("chunkId");
        json = Json.of("{ method: 'upload', executorId: '1' }");
        json.set("jobId", jm.jobId);
        json.set("chunkId", chunkId);
        response = http.header(JobMessage.KARATE_JOB, json.toString()).postJson("{}");
        jobHeader = response.getHeader(JobMessage.KARATE_JOB);
        json = Json.of(jobHeader);
        matchContains(json.asMap(), "{ method: 'upload', chunkId: '1' }");
        json = Json.of("{ method: 'next', executorId: '1' }");
        json.set("jobId", jm.jobId);
        response = http.header(JobMessage.KARATE_JOB, json.toString()).postJson("{}");
        jobHeader = response.getHeader(JobMessage.KARATE_JOB);
        json = Json.of(jobHeader);
        matchContains(json.asMap(), "{ method: 'next', chunkId: '2' }");        
        json = Json.of("{ method: 'next', executorId: '1' }");
        json.set("jobId", jm.jobId);
        response = http.header(JobMessage.KARATE_JOB, json.toString()).postJson("{}");
        jobHeader = response.getHeader(JobMessage.KARATE_JOB);
        json = Json.of(jobHeader);
        matchContains(json.asMap(), "{ method: 'stop' }");         
        jm.server.stop();
    }

}
