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
        ScriptEnv env = new ScriptEnv(false, "dev", new File(featureDir), null, getClass().getClassLoader());
        ScriptContext ctx = new ScriptContext(env, null, null);        
        ScriptValue value = Script.evalInNashorn("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
