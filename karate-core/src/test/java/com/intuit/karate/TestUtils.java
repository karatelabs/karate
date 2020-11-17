package com.intuit.karate;

import com.intuit.karate.Suite;
import com.intuit.karate.FileUtils;
import com.intuit.karate.Logger;
import com.intuit.karate.Resource;
import com.intuit.karate.Runner;
import com.intuit.karate.core.Config;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.core.ScenarioEngine;
import com.intuit.karate.core.ScenarioGenerator;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.match.Match;
import com.intuit.karate.match.MatchResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.thymeleaf.util.StringUtils;

/**
 *
 * @author pthomas3
 */
public class TestUtils {

    public static void match(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).isEqualTo(expected);
        assertTrue(mr.pass, mr.message);
    }

    public static void matchContains(Object actual, Object expected) {
        MatchResult mr = Match.that(actual).contains(expected);
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
            InputStream is = FileUtils.toInputStream(text);
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

    }

}
