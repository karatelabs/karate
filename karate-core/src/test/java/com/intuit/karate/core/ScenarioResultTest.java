package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.JsonUtils;
import org.junit.jupiter.api.Test;
import static com.intuit.karate.TestUtils.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ScenarioResultTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioResultTest.class);

    @Test
    void testFromJson() {
        File file = new File("src/test/java/com/intuit/karate/core/executor-result.json");
        String json = FileUtils.toString(file);
        List<Map<String, Object>> list = Json.of(json).get("$[0].elements");
        Feature feature = Feature.read("classpath:com/intuit/karate/core/executor-result.feature");
        Scenario scenario = feature.getScenario(0, -1);
        logger.debug("scenario: {}", scenario);
        ScenarioResult sr = new ScenarioResult(scenario, list, true);
        Map<String, Object> map = sr.toMap();
        logger.debug("json: {}", JsonUtils.toJson(map));
        FeatureRuntime fr = FeatureRuntime.of(feature);
        fr.result.addResult(sr);
        HtmlFeatureReport.saveFeatureResult("target", fr.result);
    }

}
