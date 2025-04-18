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
 * @author OwenK2
 */
class ScenarioOutlineResultTest {
    
    static final Logger logger = LoggerFactory.getLogger(ScenarioOutlineResultTest.class);

    @Test
    void testJsonConversion() {
        FeatureRuntime fr = TestUtils.runFeature("classpath:com/intuit/karate/core/scenario-outline-result.feature");
        assertFalse(fr.result.isFailed());
    }

}
