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
        ScriptEnv env = new ScriptEnv("dev", new File(featureDir), null, getClass().getClassLoader(), null);
        CallContext callContext = new CallContext(null, 0, null, -1, false, true, null);
        ScriptContext ctx = new ScriptContext(env, callContext);        
        ScriptValue value = Script.evalInNashorn("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
