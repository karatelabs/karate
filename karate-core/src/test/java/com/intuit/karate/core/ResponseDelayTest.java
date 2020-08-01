package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.HttpResponse;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
public class ResponseDelayTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseDelayTest.class);
    private static FeaturesBackend featuresBackend;

    @BeforeClass
    public static void setupFeature() {

        Feature feature = FeatureParser.parse(FileUtils.getFileRelativeTo(ResponseDelayTest.class, "responseDelay.feature"));
        featuresBackend = new FeaturesBackend(feature);
    }

    private HttpRequest getRequest(String path) {
        HttpRequest req = new HttpRequest();
        req.setUri(path);
        req.setBody(new byte[0]);
        req.setMethod("GET");
        return req;
    }

    @Test
    public void testFeatureLevelDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/feature-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "300");
    }

    @Test
    public void testAfterScenarioDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/after-scenario-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "200");
    }

    @Test
    public void testScenarioDelay() {
        HttpResponse resp = featuresBackend.buildResponse(getRequest("/scenario-delay"), System.currentTimeMillis());
        Match.equals(resp.getDelay(), "100");
    }


}
