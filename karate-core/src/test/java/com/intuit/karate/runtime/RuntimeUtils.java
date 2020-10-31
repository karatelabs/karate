package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.Resource;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.server.HttpClient;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.function.Function;

/**
 *
 * @author pthomas3
 */
public class RuntimeUtils {

    public static ScenarioEngine engine() {
        return new ScenarioEngine(new Config(), runtime(), new HashMap(), new Logger());
    }

    public static Feature toFeature(String... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature:\nScenario:\n");
        for (String line : lines) {
            sb.append("* ").append(line).append('\n');
        }
        InputStream is = FileUtils.toInputStream(sb.toString());
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Path path = FileUtils.fromRelativeClassPath("classpath:com/intuit/karate/runtime/dummy.feature", cl);
        Resource resource = new Resource(path, cl) {
            @Override
            public InputStream getStream() {
                return is;
            }
        };
        return FeatureParser.parse(resource);
    }

    public static ScenarioRuntime runtime() {
        Feature feature = toFeature("* print 'test'");
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        sg.hasNext();
        return sg.next();
    }

    public static ScenarioRuntime runScenario(Function<ScenarioEngine, HttpClient> clientFactory, String... lines) {
        return run(clientFactory, toFeature(lines));
    }

    public static ScenarioRuntime run(Function<ScenarioEngine, HttpClient> clientFactory, Feature feature) {
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        sg.hasNext();
        ScenarioRuntime sr = sg.next();
        if (clientFactory != null) {
            sr.engine.configure("clientFactory", new Variable(clientFactory));
        }
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
