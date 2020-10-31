package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
class ResponseDelayTest {

    static final Logger logger = LoggerFactory.getLogger(ResponseDelayTest.class);
    static FeaturesBackend featuresBackend;

    @BeforeAll
    static void setupFeature() {
        Feature feature = FeatureParser.parse(FileUtils.getFileRelativeTo(ResponseDelayTest.class, "responseDelay.feature"));
        featuresBackend = new FeaturesBackend(feature);
    }

    HttpRequest getRequest(String path) {
        HttpRequest req = new HttpRequest();
        req.setUri(path);
        req.setBody(new byte[0]);
        req.setMethod("GET");
        return req;
    }

    @Test
    void testFeatureLevelDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/feature-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "300");
    }

    @Test
    void testAfterScenarioDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/after-scenario-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "200");
    }

    @Test
    void testScenarioDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/scenario-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "100");
    }

}
