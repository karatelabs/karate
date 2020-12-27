package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Json;
import com.intuit.karate.TestUtils;
import static com.intuit.karate.TestUtils.*;
import java.io.File;
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
class ScenarioResultTest {
    
    static final Logger logger = LoggerFactory.getLogger(ScenarioResultTest.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name);
        assertFalse(fr.result.isFailed());
        return fr;
    }

    @Test
    void testKarateJson() {
        run("scenario-result.feature");
        File file = HtmlFeatureReport.saveFeatureResult("target", fr.result);
        logger.debug("saved report1: {}", file.getAbsolutePath());
        Json json = Json.of(fr.result.toKarateJson());
        assertEquals("classpath:com/intuit/karate/core/scenario-result.feature", json.get("featurePath"));
        List<Map<String, Object>> list = json.get("scenarioResults");
        assertEquals(1, list.size());
        Map<String, Object> scenarioResult = json.get("scenarioResults[0]");
        String expected = FileUtils.toString(new File("src/test/java/com/intuit/karate/core/scenario-result.json"));
        match(scenarioResult, expected);
        Map<String, Object> json1 = json.asMap();
        FeatureResult temp = FeatureResult.fromKarateJson(fr.suite.workingDir, json1);
        Map<String, Object> json2 = Json.of(temp.toKarateJson()).asMap();
        match(json1, json2);
    }

}
