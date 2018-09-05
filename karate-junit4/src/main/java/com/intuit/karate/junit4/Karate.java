package com.intuit.karate.junit4;

import com.intuit.karate.FileResource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import cucumber.api.CucumberOptions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Karate extends ParentRunner<Feature> {

    private static final Logger logger = LoggerFactory.getLogger(Karate.class);

    private final List<Feature> children;
    private final String tagSelector;

    public Karate(Class<?> clazz) throws InitializationError, IOException {
        super(clazz);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }
        CucumberOptions co = clazz.getAnnotation(CucumberOptions.class);
        List<String> tags;
        List<String> features;
        if (co == null) {
            logger.warn("CucumberOptions annotation not found on class: {}", clazz);
            tags = null;
            features = null;
        } else {
            String[] tagsArray = co.tags();
            tags = Arrays.asList(tagsArray);
            String[] featuresArray = co.features();
            features = Arrays.asList(featuresArray);
        }
        if (features == null || features.isEmpty()) {
            String relative = FileUtils.toRelativeClassPath(clazz);
            features = Collections.singletonList(relative);
        }
        List<FileResource> resources = FileUtils.scanForFeatureFiles(features);
        children = new ArrayList(resources.size());
        for (FileResource fr : resources) {
            Feature feature = FeatureParser.parse(fr.file, fr.relativePath);
            children.add(feature);
        }
        tagSelector = Engine.fromCucumberOptionsTags(tags);
    }

    @Override
    public List<Feature> getChildren() {
        return children;
    }

    private static Description getScenarioDescription(String featureName, Scenario scenario) {
        return Description.createTestDescription(featureName, scenario.getDisplayMeta() + ' ' + scenario.getName());
    }
    
    private static String getFeatureName(Feature feature) {
        return "[" + feature.getFile().getName() + "]";
    }

    @Override
    protected Description describeChild(Feature child) {
        return Description.createSuiteDescription(getFeatureName(child), child.getPackageQualifiedName());
    }

    @Override
    protected void runChild(Feature child, RunNotifier notifier) {
        FeatureResult result = Engine.executeFeatureSync(child, tagSelector, null);
        for (ScenarioResult sr : result.getScenarioResults()) {
            Description scenarioDescription = getScenarioDescription(getFeatureName(child), sr.getScenario());
            notifier.fireTestStarted(scenarioDescription);
            if (sr.isFailed()) {
                notifier.fireTestFailure(new Failure(scenarioDescription, sr.getError()));
            }
            notifier.fireTestFinished(scenarioDescription);
        }
        Engine.saveResultHtml(Engine.getBuildDir() + File.separator + "surefire-reports", result);
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
    }

}
