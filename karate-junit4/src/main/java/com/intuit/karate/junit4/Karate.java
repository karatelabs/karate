package com.intuit.karate.junit4;

import com.intuit.karate.CallContext;
import com.intuit.karate.cucumber.DummyFormatter;
import com.intuit.karate.cucumber.DummyReporter;
import com.intuit.karate.cucumber.KarateFeature;
import com.intuit.karate.cucumber.KarateHtmlReporter;
import com.intuit.karate.cucumber.KarateRuntime;
import com.intuit.karate.cucumber.KarateRuntimeOptions;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitOptions;
import cucumber.runtime.junit.JUnitReporter;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Step;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * implementation adapted from cucumber.api.junit.Cucumber
 * 
 * @author pthomas3
 */
public class Karate extends ParentRunner<FeatureRunner> {
    
    private static Logger logger;
    
    private final List<FeatureRunner> children;
    
    private final JUnitReporter reporter;
    private final KarateHtmlReporter htmlReporter;    
    private final Map<String, KarateFeatureRunner> featureMap;

    public Karate(Class clazz) throws InitializationError, IOException {
        super(clazz);
        logger = LoggerFactory.getLogger(Karate.class);
        List<FrameworkMethod> testMethods = getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
            logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }
        KarateRuntimeOptions kro = new KarateRuntimeOptions(clazz);
        RuntimeOptions ro = kro.getRuntimeOptions();
        JUnitOptions junitOptions = new JUnitOptions(ro.getJunitOptions());
        htmlReporter = new KarateHtmlReporter(new DummyReporter(), new DummyFormatter());
        reporter = new JUnitReporter(htmlReporter, htmlReporter, ro.isStrict(), junitOptions) {
            final List<Step> steps = new ArrayList();
            final List<Match> matches = new ArrayList();
            @Override
            public void startOfScenarioLifeCycle(Scenario scenario) {
                steps.clear();
                matches.clear();
                super.startOfScenarioLifeCycle(scenario);
            }                       
            @Override
            public void step(Step step) {
                steps.add(step);                
            }
            @Override
            public void match(Match match) {
                matches.add(match);
            }            
            @Override
            public void result(Result result) {
                Step step = steps.remove(0);
                Match match = matches.remove(0);
                CallContext callContext = new CallContext(null, 0, null, -1, false, false, null);
                // all the above complexity was just to be able to do this
                htmlReporter.karateStep(step, match, result, callContext);
                // this may not work for things other than the cucumber 'native' json formatter
                super.step(step);
                super.match(match);
                super.result(result);
            }
            @Override
            public void eof() {
                try {
                    super.eof();
                } catch (Exception e) {
                    logger.warn("WARNING: cucumber native plugin / formatter failed: " + e.getMessage());
                }
            }
        };  
        List<KarateFeature> list = KarateFeature.loadFeatures(kro);
        children = new ArrayList(list.size());
        featureMap = new HashMap(list.size());
        for (KarateFeature kf : list) {
            KarateRuntime kr = kf.getRuntime(htmlReporter);
            FeatureRunner runner = new FeatureRunner(kf.getFeature(), kr, reporter);
            children.add(runner);
            featureMap.put(runner.getName(), new KarateFeatureRunner(kf, kr));
        }
    }
    
    @Override
    public List<FeatureRunner> getChildren() {
        return children;
    }        
    
    @Override
    protected Description describeChild(FeatureRunner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        KarateFeatureRunner kfr = featureMap.get(child.getName());
        KarateRuntime karateRuntime = kfr.runtime;
        htmlReporter.startKarateFeature(kfr.feature.getFeature());
        child.run(notifier);
        karateRuntime.afterFeature();
        karateRuntime.printSummary();
        htmlReporter.endKarateFeature();
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        if (reporter != null) { // can happen for zero features found
            reporter.done();
            reporter.close();
        }
    }   

}
