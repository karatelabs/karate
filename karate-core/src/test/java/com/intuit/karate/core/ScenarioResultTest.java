package com.intuit.karate.core;

import com.intuit.karate.Json;
import com.intuit.karate.TestUtils;
import static com.intuit.karate.TestUtils.*;
import static com.intuit.karate.core.FeatureRuntimeTest.logger;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ScenarioResultTest {

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name);
        assertFalse(fr.result.isFailed());
        return fr;
    }

    @Test
    void testKarateJson() {
        run("scenario-result.feature");
        File file = HtmlFeatureReport.saveFeatureResult("target/temp1", fr.result);
        logger.debug("saved report1: {}", file.getAbsolutePath());
        Map<String, Object> json1 = Json.of(fr.result.toKarateJson()).asMap();
        FeatureResult temp = FeatureResult.fromKarateJson(fr.suite.workingDir, json1);
        file = HtmlFeatureReport.saveFeatureResult("target/temp2", temp);
        logger.debug("saved report2: {}", file.getAbsolutePath());
        Map<String, Object> json2 = Json.of(temp.toKarateJson()).asMap();
        match(json1, json2);
    }

}
