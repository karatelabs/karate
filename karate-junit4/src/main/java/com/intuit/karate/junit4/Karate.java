package com.intuit.karate.junit4;

import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.RunnerOptions;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.intuit.karate.core.Tags;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Description> featureMap;
    private final String tagSelector;

    public Karate(Class<?> clazz) throws InitializationError, IOException {
        super(clazz);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(clazz);
        List<Resource> resources = FileUtils.scanForFeatureFiles(options.getFeatures(), clazz.getClassLoader());
        children = new ArrayList(resources.size());
        featureMap = new HashMap(resources.size());
        for (Resource resource : resources) {
            Feature feature = FeatureParser.parse(resource);
            children.add(feature);
            Description featureDescription = Description.createSuiteDescription(
                    getFeatureName(feature), feature.getResource().getPackageQualifiedName());
            featureMap.put(feature.getRelativePath(), featureDescription);
            for (Scenario s : feature.getScenarios()) {
                Description scenarioDescription = getScenarioDescription(getFeatureName(feature), s);
                featureDescription.addChild(scenarioDescription);
            }
        }
        tagSelector = Tags.fromCucumberOptionsTags(options.getTags());
    }

    @Override
    public List<Feature> getChildren() {
        return children;
    }

    private static Description getScenarioDescription(String featureName, Scenario scenario) {
        String scenarioKey;
        if (scenario.isDynamic() || scenario.isBackgroundDone()) {
            // hack otherwide eclipse "unrooted tests" in junit view
            scenarioKey = scenario.getDisplayMeta();
        } else {
            scenarioKey = scenario.getDisplayMeta() + ' ' + scenario.getName();
        }
        return Description.createTestDescription(featureName, scenarioKey);
    }

    private static String getFeatureName(Feature feature) {
        return "[" + feature.getResource().getFileNameWithoutExtension() + "]";
    }

    @Override
    protected Description describeChild(Feature child) {
        return featureMap.get(child.getRelativePath());
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
