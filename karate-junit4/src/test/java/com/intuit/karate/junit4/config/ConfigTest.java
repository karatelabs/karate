package com.intuit.karate.junit4.config;

import com.intuit.karate.Runner;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ConfigTest {

    @Test
    public void testOverrideDir() {
        System.setProperty("karate.config.dir", "src/test/java/com/intuit/karate/junit4/config");
        System.setProperty("karate.env", "custom");
        Runner.runFeature(this.getClass(), "config-dir.feature", null, true);
        System.clearProperty("karate.config.dir");
    }

    @Test
    public void testOverrideEnvAndDir() {        
        System.setProperty("karate.env", "confenvdir");
        System.setProperty("karate.config.dir", "conf");
        Runner.runFeature(this.getClass(), "config-envdir.feature", null, true);
        System.clearProperty("karate.config.dir");
    }
    
    @Test
    public void testOverrideEnv() {        
        System.setProperty("karate.env", "confenv");
        Runner.runFeature(this.getClass(), "config-env.feature", null, true);
    }    

}
