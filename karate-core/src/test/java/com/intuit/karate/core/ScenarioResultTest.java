package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.Match;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class ScenarioResultTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ScenarioResultTest.class);
    
    @Test
    public void testJsonToScenarioResult() {
        String json = FileUtils.toString(getClass().getResourceAsStream("simple1.json"));
        List<Map<String, Object>> list = JsonUtils.toJsonDoc(json).read("$[0].elements");
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/core/simple1.feature");
        Scenario scenario = feature.getSections().get(0).getScenario();
        ScenarioResult sr = new ScenarioResult(scenario, list, true);
        Match.init(list.get(0)).equalsObject(sr.backgroundToMap());
        Match.init(list.get(1)).equalsObject(sr.toMap());
    }
    
}
