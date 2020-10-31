package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Match;
import com.intuit.karate.ScriptValueMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureBackendTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureBackendTest.class);

    ScriptValueMap getRequest(String name) {
        return new Match()
                .text("name", name)
                .def("request", "{ name: '#(name)' }").vars();
    }

    @Test
    void testServer() {
        Feature feature = FeatureParser.parse(FileUtils.getFileRelativeTo(getClass(), "server.feature"));
        FeaturesBackend backend = new FeaturesBackend(feature);
        ScriptValueMap vars = backend.handle(getRequest("Billie"));
        Match.equals(vars.get("response").getAsMap(), "{ id: 1, name: 'Billie' }");
        vars = backend.handle(getRequest("Wild"));
        Match.equals(vars.get("response").getAsMap(), "{ id: 2, name: 'Wild' }");
        List<Map> list = vars.get("cats").getAsList();
        Match.equals(list, "[{ id: 1, name: 'Billie' }, { id: 2, name: 'Wild' }]");
    }

    @Test
    void testMultipleServerFeatures() {
        Feature feature = FeatureParser.parse(FileUtils.getFileRelativeTo(getClass(), "server.feature"));
        Feature feature2 = FeatureParser.parse(FileUtils.getFileRelativeTo(getClass(), "server-path-matching.feature"));
        FeaturesBackend backend = new FeaturesBackend(new Feature[]{feature, feature2});

        Match match = new Match()
                .text(ScriptValueMap.VAR_REQUEST_URI, "/v10/cats")
                .text(ScriptValueMap.VAR_REQUEST_METHOD, "GET");

        FeatureBackend.FeatureScenarioMatch matchingInfo = backend.getMatchingScenario(match.vars());

        assertNotNull(matchingInfo.getFeatureBackend());
        assertNotNull(matchingInfo.getScenario());
        assertEquals("server-path-matching.feature", matchingInfo.getFeatureBackend().getFeatureName());
        assertTrue(matchingInfo.getScenario().getName().contains("/v10/cats"));
    }

}
