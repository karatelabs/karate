package com.intuit.karate.report;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ReportUtilsTest {

    static final Logger logger = LoggerFactory.getLogger(ReportUtilsTest.class);

    @Test
    void testReport() {
        Feature feature = Feature.read("classpath:com/intuit/karate/report/test.feature");
        FeatureRuntime fr = FeatureRuntime.of(feature);
        fr.run();
        Report report = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        report.render("target/report-test");
    }

}
