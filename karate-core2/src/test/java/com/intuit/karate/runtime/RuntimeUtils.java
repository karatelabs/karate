package com.intuit.karate.runtime;

import com.intuit.karate.Resource;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import java.nio.file.Paths;

/**
 *
 * @author pthomas3
 */
public class RuntimeUtils {

    public static ScenarioRuntime runScenario(String... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature:\nScenario:\n");
        for (String line : lines) {
            sb.append("* ").append(line).append('\n');
        }
        Feature feature = FeatureParser.parse(Resource.of(Paths.get("target/temp.feature"), sb.toString()));
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature, ScenarioCall.NONE);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        sg.hasNext();
        ScenarioRuntime sr = sg.next();
        sr.run();
        return sr;
    }

    public static FeatureRuntime runFeature(String path) {
        Feature feature = FeatureParser.parse(path);
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature, ScenarioCall.NONE);
        fr.run();
        return fr;
    }

}
