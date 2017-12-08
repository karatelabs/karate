package com.intuit.karate.testng;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.KarateRuntime;
import com.intuit.karate.cucumber.KarateRuntimeOptions;
import cucumber.api.testng.CucumberExceptionWrapper;
import cucumber.api.testng.CucumberFeatureWrapper;
import cucumber.api.testng.CucumberFeatureWrapperImpl;
import cucumber.api.testng.FeatureResultListener;
import cucumber.api.testng.TestNgReporter;
import cucumber.runtime.CucumberException;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import java.io.File;
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

    private KarateRuntimeOptions runtimeOptions;
    private FeatureResultListener resultListener;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        runtimeOptions = new KarateRuntimeOptions(getClass());
        TestNgReporter reporter = new TestNgReporter(System.out);
        RuntimeOptions ro = runtimeOptions.getRuntimeOptions();
        resultListener = new FeatureResultListener(ro.reporter(runtimeOptions.getClassLoader()), ro.isStrict());
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        RuntimeOptions ro = runtimeOptions.getRuntimeOptions();
        Formatter formatter = ro.formatter(runtimeOptions.getClassLoader());        
        formatter.done();
        formatter.close();
    }

    @Test(groups = "cucumber", description = "Runs Cucumber Feature", dataProvider = "features")
    public void feature(CucumberFeatureWrapper wrapper) {
        CucumberFeature feature = wrapper.getCucumberFeature();
        File file = FileUtils.resolveIfClassPath(feature.getPath());
        KarateRuntime runtime = runtimeOptions.getRuntime(file, null);
        resultListener.startFeature();
        RuntimeOptions ro = runtimeOptions.getRuntimeOptions();
        feature.run(ro.formatter(runtimeOptions.getClassLoader()), resultListener, runtime);
        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
        runtime.afterFeature();
        runtime.printSummary();        
    }

    @DataProvider
    public Object[][] features() {
        try {
            List<CucumberFeature> features = runtimeOptions.loadFeatures();
            List<Object[]> featuresList = new ArrayList<Object[]>(features.size());
            features.forEach(feature -> featuresList.add(new Object[]{new CucumberFeatureWrapperImpl(feature)}));
            return featuresList.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e)}};
        }
    }

}
