package com.intuit.karate.runtime;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Resource;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.thymeleaf.util.StringUtils;

/**
 *
 * @author pthomas3
 */
public class FeatureBuilder {

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
        Path path = FileUtils.fromRelativeClassPath("classpath:com/intuit/karate/runtime/dummy.feature", cl);
        Resource resource = new Resource(path, cl) {
            @Override
            public InputStream getStream() {
                return is;
            }
        };
        return FeatureParser.parse(resource);
    }

}
