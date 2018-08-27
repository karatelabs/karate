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
        String featureDir = FileUtils.getDirContaining(getClass()).getPath();
        ScriptEnv env = new ScriptEnv("dev", null, new File(featureDir), null, getClass().getClassLoader());
        CallContext callContext = new CallContext(null, true);
        ScriptContext ctx = new ScriptContext(env, callContext);        
        ScriptValue value = Script.evalJsExpression("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
