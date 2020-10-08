package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Resource;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author pthomas3
 */
public class RuntimeUtils {
    
    public static Feature toFeature(String name, String ... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature:\nScenario:\n");
        for (String line : lines) {
            sb.append("* ").append(line).append('\n');
        }
        Path path = FileUtils.fromRelativeClassPath("classpath:com/intuit/karate/runtime/"  + name, ClassLoader.getSystemClassLoader());
        Resource resource = Resource.of(path, sb.toString());
        return FeatureParser.parse(resource);        
    }

    public static ScenarioRuntime runScenario(String... lines) {
        Feature feature = toFeature("print.feature", lines);
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        sg.hasNext();
        ScenarioRuntime sr = sg.next();
        sr.run();
        return sr;
    }

    public static FeatureRuntime runFeature(String path) {
        Feature feature = FeatureParser.parse(path);
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature);
        fr.run();
        return fr;
    }

}
