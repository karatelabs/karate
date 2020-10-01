package com.intuit.karate.runtime;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.core.HtmlFeatureReport;
import com.intuit.karate.core.HtmlSummaryReport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class FeatureRuntimeTest {

    static final Logger logger = LoggerFactory.getLogger(FeatureRuntimeTest.class);

    @Test
    void testSimple() {
        Feature feature = FeatureParser.parse("classpath:com/intuit/karate/runtime/simple.feature");
        FeatureRuntime fr = new FeatureRuntime(new SuiteRuntime(), feature);
        fr.run();
        String reportDir = "target/temp";
        HtmlSummaryReport summary = new HtmlSummaryReport();
        HtmlFeatureReport.saveFeatureResult(reportDir, fr.result);
        summary.addFeatureResult(fr.result);
        summary.save(reportDir);
    }

}
