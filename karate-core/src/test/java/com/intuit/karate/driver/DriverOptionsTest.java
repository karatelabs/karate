package com.intuit.karate.driver;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class DriverOptionsTest {

    private static void test(String in, String out) {
        assertEquals(out, DriverOptions.preProcessWildCard(in));
    }

    @Test
    public void testPreProcess() {
        test("{}hi", "//*[normalize-space(text())='hi']");
        test("{^}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:0}hi", "//*[contains(normalize-space(text()),'hi')]");
        test("{^:2}hi", "/(//*[contains(normalize-space(text()),'hi')])[2]");
        test("{:2}hi", "/(//*[normalize-space(text())='hi'])[2]");
        test("{a}hi", "//a[normalize-space(text())='hi']");
        test("{a:2}hi", "/(//a[normalize-space(text())='hi'])[2]");        
        test("{^a:}hi", "//a[contains(normalize-space(text()),'hi')]");
        test("{^a/p}hi", "//a/p[contains(normalize-space(text()),'hi')]");
        test("{^a:2}hi", "/(//a[contains(normalize-space(text()),'hi')])[2]");
    }
    
    private ScenarioContext getContext() {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, null, null);
    }    
    
    @Test
    public void testRetry() {
        DriverOptions options = new DriverOptions(getContext(), Collections.EMPTY_MAP, null, 0, null);
        options.retry(() -> 1, x -> x < 5, "not 5", false);        
    }

}
