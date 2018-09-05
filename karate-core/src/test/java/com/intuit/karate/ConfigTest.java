package com.intuit.karate;

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class ConfigTest {
    
    @Test
    public void testSettingVariableViaKarateConfig() {
        File featureDir = FileUtils.getDirContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir(featureDir);
        CallContext callContext = new CallContext(null, true);
        ScenarioContext ctx = new ScenarioContext(featureContext, callContext);        
        ScriptValue value = Script.evalJsExpression("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
