package com.intuit.karate;

import com.intuit.karate.core.Config;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.ScenarioIterator;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.runner.NoopDriver;
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.resource.MemoryResource;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.thymeleaf.util.StringUtils;

/**
 *
 * @author pthomas3
 */
public class TestUtils {

    public static void match(Object actual, Object expected) {
        Match.Result mr = Match.evaluate(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    public static void matchContains(Object actual, Object expected) {
        Match.Result mr = Match.evaluate(actual).contains(expected);
        assertTrue(mr.pass, mr.message);
    }

    public static ScenarioEngine engine() {
        return new ScenarioEngine(new Config(), runtime(), new HashMap(), new Logger());
    }

    public static Feature toFeature(String... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Feature:\nScenario:\n");
        for (String line : lines) {
            sb.append("* ").append(line).append('\n');
        }
        File file = ResourceUtils.getFileRelativeTo(TestUtils.class, "core/dummy.feature");
        Resource resource = new MemoryResource(file, sb.toString());
        return Feature.read(resource);
    }

    public static ScenarioRuntime runtime() {
        Feature feature = toFeature("* print 'test'");
        FeatureRuntime fr = FeatureRuntime.of(feature);
        return new ScenarioIterator(fr).first();
    }

    public static ScenarioRuntime runScenario(HttpClientFactory clientFactory, String... lines) {
        return run(clientFactory, toFeature(lines));
    }

    public static ScenarioRuntime run(HttpClientFactory clientFactory, Feature feature) {
        Runner.Builder builder = Runner.builder();
        builder.clientFactory(clientFactory);
        String configDir = System.getProperty("karate.config.dir");
        if (configDir != null) {
            builder.configDir = configDir;
        }
        FeatureRuntime fr = FeatureRuntime.of(new Suite(builder), feature);
        ScenarioRuntime sr = new ScenarioIterator(fr).first();
        sr.run();
        return sr;
    }

    public static FeatureRuntime runFeature(String path) {
        return runFeature(path, null);
    }

    public static FeatureRuntime runFeature(String path, String configDir) {
        Map<String, DriverRunner> customDrivers = new HashMap<>();
        customDrivers.put(NoopDriver.DRIVER_TYPE, NoopDriver::start);
        Feature feature = Feature.read(path);
        Runner.Builder rb = Runner.builder();
        rb.features(feature);
        rb.configDir(configDir);
        rb.customDrivers(customDrivers);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(rb), feature);
        fr.run();
        return fr;
    }

    public static class FeatureBuilder {

        private final List<String> list = new ArrayList();

        public FeatureBuilder() {
            list.add("Feature:");
            list.add("\n");
        }

        public static FeatureBuilder background(String... lines) {
            FeatureBuilder fb = new FeatureBuilder();
            if (lines.length > 0) {
                fb.list.add("Background:");
                for (String line : lines) {
                    fb.list.add("* " + line);
                }
                fb.list.add("\n");
            }
            return fb;
        }

        public FeatureBuilder scenario(String exp, String... lines) {
            list.add("Scenario: " + exp);
            for (String line : lines) {
                list.add("* " + line);
            }
            list.add("\n");
            return this;
        }

        public Feature build() {
            String text = StringUtils.join(list, '\n');
            File file = ResourceUtils.getFileRelativeTo(getClass(), "core/dummy.feature");
            Resource resource = new MemoryResource(file, text);
            return Feature.read(resource);
        }

    }

}
