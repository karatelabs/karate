package com.intuit.karate.cucumber;

import com.intuit.karate.StepDefs;
import cucumber.api.java.ObjectFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KarateObjectFactory implements ObjectFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateObjectFactory.class);
       
    private final String featureDir;
    private final ClassLoader fileClassLoader;
    private final String env;
    private StepDefs stepDefs;        
    
    public KarateObjectFactory(String featureDir, ClassLoader fileClassLoader, String env) {
        this.featureDir = featureDir;
        this.fileClassLoader = fileClassLoader;
        this.env = env;
    }

    @Override
    public void start() {
        logger.trace("start");
    }

    @Override
    public void stop() {
        logger.trace("stop");
    }

    @Override
    public boolean addClass(Class<?> glueClass) {
        if (logger.isTraceEnabled()) {
            logger.trace("add class: {}", glueClass);
        }
        return true;
    }

    @Override
    public <T> T getInstance(Class<T> glueClass) {
        if (stepDefs == null) {
            // the lazy init gives users the chance to over-ride the env
            // for example using a JUnit @BeforeClass hook
            logger.trace("lazy init of step defs");
            String karateEnv = StringUtils.trimToNull(env);
            if (karateEnv == null) {
                karateEnv = StringUtils.trimToNull(System.getProperty("karate.env"));
                logger.debug("obtained 'karate.env' from system properties: {}", karateEnv);
            }
            stepDefs = new StepDefs(featureDir, fileClassLoader, karateEnv);
        }
        return (T) stepDefs;
    }

    public StepDefs getStepDefs() {
        return stepDefs;
    }        
    
}
