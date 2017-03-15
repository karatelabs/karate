package com.intuit.karate.testng;

import com.intuit.karate.cucumber.CucumberRunner;
import cucumber.api.testng.CucumberExceptionWrapper;
import cucumber.api.testng.CucumberFeatureWrapper;
import cucumber.api.testng.CucumberFeatureWrapperImpl;
import cucumber.api.testng.FeatureResultListener;
import cucumber.api.testng.TestNgReporter;
import cucumber.runtime.CucumberException;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.model.CucumberFeature;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * adapted from cucumber.api.testng.AbstractTestNGCucumberTests
 *
 * @author pthomas3
 */
public abstract class KarateRunner {

    private CucumberRunner runner;
    private FeatureResultListener resultListener;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        runner = new CucumberRunner(getClass());
        TestNgReporter reporter = new TestNgReporter(System.out);
        RuntimeOptions ro = runner.getRuntimeOptions();
        resultListener = new FeatureResultListener(ro.reporter(runner.getClassLoader()), ro.isStrict());
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        runner.finish();
    }

    @Test(groups = "cucumber", description = "Runs Cucumber Feature", dataProvider = "features")
    public void feature(CucumberFeatureWrapper wrapper) {
        CucumberFeature feature = wrapper.getCucumberFeature();
        Runtime runtime = runner.getRuntime(feature);
        resultListener.startFeature();
        RuntimeOptions ro = runner.getRuntimeOptions();
        feature.run(ro.formatter(runner.getClassLoader()), resultListener, runtime);
        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
        runtime.printSummary();        
    }

    @DataProvider
    public Object[][] features() {
        try {
            List<CucumberFeature> features = runner.getFeatures();
            List<Object[]> featuresList = new ArrayList<Object[]>(features.size());
            for (CucumberFeature feature : features) {
                featuresList.add(new Object[]{new CucumberFeatureWrapperImpl(feature)});
            }
            return featuresList.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e)}};
        }
    }

}
