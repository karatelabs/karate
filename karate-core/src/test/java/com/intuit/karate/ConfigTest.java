package com.intuit.karate;

import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class ConfigTest {
    
    @Test
    public void testSettingVariableViaKarateConfig() {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir(featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        ScenarioContext ctx = new ScenarioContext(featureContext, callContext, null, null);        
        ScriptValue value = Script.evalJsExpression("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
