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
        ScriptEnv env = new ScriptEnv("dev", null, featureDir, null);
        CallContext callContext = new CallContext(null, true);
        ScenarioContext ctx = new ScenarioContext(env, callContext);        
        ScriptValue value = Script.evalJsExpression("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
