package com.intuit.karate.runtime;

import com.intuit.karate.Resource;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author pthomas3
 */
class ScenarioRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(ScenarioRuntimeTest.class);

    private ScenarioRuntime scenario(String ... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature:\nScenario:\n");
        for (String line : lines) {
            sb.append("* ").append(line).append('\n');
        }
        Feature feature = FeatureParser.parse(Resource.of(Paths.get("target/temp.feature"), sb.toString()));
        SuiteRuntime suiteRuntime = new SuiteRuntime();
        FeatureRuntime featureRuntime = new FeatureRuntime(suiteRuntime, feature);
        ScenarioGenerator generator = new ScenarioGenerator(featureRuntime, feature.getSections().iterator());
        generator.hasNext();
        ScenarioRuntime scenarioRuntime = generator.next();
        scenarioRuntime.run();
        return scenarioRuntime;
    }

    @Test
    void testDef() {
        ScenarioRuntime sr = scenario("print 'hello'", "def a = 1 + 2");
        Variable a = sr.engine.eval("a");
        assertEquals(3, a.<Number>getValue());
    }

}
