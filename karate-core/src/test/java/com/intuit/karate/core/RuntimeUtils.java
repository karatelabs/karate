package com.intuit.karate.core;

import com.intuit.karate.Suite;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.Resource;
import com.intuit.karate.Runner;
import com.intuit.karate.http.HttpClientFactory;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

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
        Path path = FileUtils.fromRelativeClassPath("classpath:com/intuit/karate/core/dummy.feature", cl);
        Resource resource = new Resource(path, cl) {
            @Override
            public InputStream getStream() {
                return is;
            }
        };
        return Feature.read(resource);
    }

    public static ScenarioRuntime runtime() {
        Feature feature = toFeature("* print 'test'");
        FeatureRuntime fr = FeatureRuntime.of(feature);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        return sg.next();
    }

    public static ScenarioRuntime runScenario(HttpClientFactory clientFactory, String... lines) {
        return run(clientFactory, toFeature(lines));
    }

    public static ScenarioRuntime run(HttpClientFactory clientFactory, Feature feature) {
        Runner.Builder builder = Runner.builder();
        builder.clientFactory(clientFactory);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(builder), feature);
        ScenarioGenerator sg = new ScenarioGenerator(fr, feature.getSections().iterator());
        ScenarioRuntime sr = sg.next();
        sr.run();
        return sr;
    }

    public static FeatureRuntime runFeature(String path) {
        return runFeature(path, null);
    }

    public static FeatureRuntime runFeature(String path, String configDir) {
        Feature feature = Feature.read(path);
        Runner.Builder rb = Runner.builder();
        rb.configDir(configDir);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(rb), feature);
        fr.run();
        return fr;
    }

}
