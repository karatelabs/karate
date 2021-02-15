package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.TestUtils;
import static com.intuit.karate.TestUtils.*;
import com.intuit.karate.report.Report;
import com.intuit.karate.report.SuiteReports;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureResultTest {
    
    static final Logger logger = LoggerFactory.getLogger(FeatureResultTest.class);

    FeatureRuntime fr;

    private FeatureRuntime run(String name) {
        fr = TestUtils.runFeature("classpath:com/intuit/karate/core/" + name);
        assertFalse(fr.result.isFailed());
        return fr;
    }

    @Test
    void testJsonConversion() {
        run("feature-result.feature");
        Map<String, Object> featureResult = fr.result.toKarateJson();
        String expected = FileUtils.toString(new File("src/test/java/com/intuit/karate/core/feature-result.json"));
        match(featureResult, expected);
        FeatureResult temp = FeatureResult.fromKarateJson(fr.suite.workingDir, featureResult);
        Report report = SuiteReports.DEFAULT.featureReport(fr.suite, fr.result);
        File file = report.render("target");        
        logger.debug("saved report: {}", file.getAbsolutePath());        
        Map<String, Object> karateClone = temp.toKarateJson();
        match(featureResult, karateClone);
        Map<String, Object> cucumberClone = temp.toCucumberJson();
        expected = FileUtils.toString(new File("src/test/java/com/intuit/karate/core/feature-result-cucumber.json"));
        match(cucumberClone, expected);
    }

}
