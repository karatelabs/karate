package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.data.Json;
import com.intuit.karate.match.Match;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ScenarioResultTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioResultTest.class);

    @Test
    void testJsonToScenarioResult() {
        String json = FileUtils.toString(getClass().getResourceAsStream("simple1.json"));
        List<Map<String, Object>> list = new Json(json).get("$[0].elements");
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/simple1.feature");
        Scenario scenario = feature.getSections().get(0).getScenario();
        ScenarioResult sr = new ScenarioResult(scenario, list, true);
        Match.that(list.get(0)).isEqualTo(sr.backgroundToMap()).isTrue();
        Match.that(list.get(1)).isEqualTo(sr.toMap()).isTrue();
    }

}
