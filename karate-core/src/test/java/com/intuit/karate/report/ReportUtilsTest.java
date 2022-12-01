package com.intuit.karate.report;

import com.intuit.karate.Runner;
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
        Feature         feature = Feature.read("classpath:com/intuit/karate/report/test.feature");
        FeatureRuntime  fr = FeatureRuntime.of(feature);
        fr.run();
        Report  report = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        File    file = report.render("target/report-test");
        String  html = FileUtils.toString(file);

        assertTrue(html.contains("<title>com.intuit.karate.report.test</title>"));
        assertTrue(html.contains("<img src=\"karate-labs-logo-ring.svg\" alt=\"Karate Labs\"/>"));
        assertTrue(html.contains("<div>Scenarios</div>"));
        assertTrue(html.contains("<a href=\"karate-summary.html\">Summary</a><span class=\"feature-label\">|</span>"));
    }
    @Test
    void testReportWithEnv() {
        final String    sEnv = "TestEnv";
        Feature         oFeature = Feature.read("classpath:com/intuit/karate/report/test.feature");
        Suite           oSuite = new Suite(Runner.builder().karateEnv(sEnv));
        FeatureRuntime  fr = FeatureRuntime.of(oSuite, new FeatureCall(oFeature));
        fr.run();
        Report          oReport = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        File            oFile = oReport.render("target/report-test-env");
        String          sHtml = FileUtils.toString(oFile);

        assertTrue(sHtml.contains("<div id=\"nav-env\">"));
        assertTrue(sHtml.contains(sEnv));
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
