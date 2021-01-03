package com.intuit.karate.template.report;

import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureRuntime;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.template.KarateTemplateEngine;
import com.intuit.karate.template.TemplateUtils;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class ReportTest {
    
    static final Logger logger = LoggerFactory.getLogger(ReportTest.class);
    
    @Test
    void testReport() {
        Feature feature = Feature.read("classpath:com/intuit/karate/template/report/test.feature");
        FeatureRuntime fr = FeatureRuntime.of(feature);
        fr.run();        
        JsEngine je = JsEngine.local();
        je.put("fr", fr.result.toKarateJson());
        KarateTemplateEngine engine = TemplateUtils.forRelativePath(je, "classpath:com/intuit/karate/report");
        String html = engine.process("feature-result.html");
        ReportUtils.initStaticResources("target/template-test");
        FileUtils.writeToFile(new File("target/template-test/" + fr.result.getFeature().getPackageQualifiedName() + ".html"), html);
    }
    
}
