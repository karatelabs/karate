package com.intuit.karate.junit4;

import com.intuit.karate.FileResource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.FeatureResult;
import cucumber.api.CucumberOptions;
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
    
    @Override
    protected Description describeChild(Feature child) {        
        return Description.createTestDescription(child.getPackageQualifiedName(), child.getName());
    }

    @Override
    protected void runChild(Feature child, RunNotifier notifier) {
        Description description = describeChild(child);
        notifier.fireTestStarted(description);
        FeatureResult result = Engine.executeFeatureSync(null, child, tagSelector, null);        
        if (result.isFailed()) {
            notifier.fireTestFailure(new Failure(description, result.getErrors().get(0)));
        } 
        notifier.fireTestFinished(description);
        Engine.saveResultHtml(Engine.getBuildDir() + "/surefire-reports", result);
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
    }   

}
