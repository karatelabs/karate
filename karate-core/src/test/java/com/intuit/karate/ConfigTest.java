package com.intuit.karate;

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
        ScriptContext ctx = new ScriptContext(false, featureDir, getClass().getClassLoader(), "dev");        
        ScriptValue value = Script.evalInNashorn("someConfig", ctx);
        assertEquals("someValue", value.getValue());
    }
    
}
