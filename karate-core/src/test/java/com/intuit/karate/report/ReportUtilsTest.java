package com.intuit.karate.report;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.Suite;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.FeatureCall;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
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

    @Test
    void testCustomTags() {
        String expectedCustomTags = "<properties><property name=\"requirement\" value=\"CALC-2\"/><property name=\"test_key\" value=\"CALC-2\"/></properties>";
        Feature feature = Feature.read("classpath:com/intuit/karate/report/customTags.feature");
        FeatureRuntime fr = FeatureRuntime.of(new Suite(), new FeatureCall(feature));
        fr.run();
        File file = ReportUtils.saveJunitXml("target", fr.result, null);        
        assertTrue(FileUtils.toString(file).contains(expectedCustomTags));
    }

}
