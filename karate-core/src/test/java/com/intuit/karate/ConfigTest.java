package com.intuit.karate;

import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ConfigTest {
    
    @Test
    void testSettingVariableViaKarateConfig() {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir(featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        ScenarioContext ctx = new ScenarioContext(featureContext, callContext, null, null);        
        ScriptValue value = Script.evalJsExpression("configSource", ctx);
        assertEquals("normal", value.getValue());
    }
    
}
