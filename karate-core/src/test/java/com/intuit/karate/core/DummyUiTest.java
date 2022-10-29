package com.intuit.karate.core;

import com.intuit.karate.Runner;
import com.intuit.karate.Suite;
import com.intuit.karate.TestUtils;
import com.intuit.karate.core.runner.NoopDriver;
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;
import com.intuit.karate.report.Report;
import com.intuit.karate.report.SuiteReports;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 * @author pthomas3
 */
public class DummyUiTest {

    FeatureRuntime fr;

    public static FeatureRuntime runFeature(String path) {
        Map<String, DriverRunner> customDrivers = new HashMap<>();
        customDrivers.put(NoopDriver.DRIVER_TYPE, NoopDriver::start);
        Feature feature = Feature.read(path);
        Runner.Builder rb = Runner.builder();
        rb.features(feature);
        rb.configDir("classpath:com/intuit/karate/core");
        rb.customDrivers(customDrivers);
        FeatureRuntime fr = FeatureRuntime.of(new Suite(rb), new FeatureCall(feature));
        fr.run();
        return fr;
    }

    @Test
    void testUiGoogle() {
        fr = runFeature("classpath:com/intuit/karate/core/dummy-ui-google.feature");
        assertFalse(fr.result.isFailed());
    }


}
