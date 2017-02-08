package com.intuit.karate.cucumber;

import com.intuit.karate.StepDefs;
import cucumber.runtime.Backend;
import cucumber.runtime.ClassFinder;
import cucumber.runtime.Glue;
import cucumber.runtime.UnreportedStepExecutor;
import cucumber.runtime.java.JavaBackend;
import cucumber.runtime.snippets.FunctionNameGenerator;
import gherkin.formatter.model.Step;
import java.lang.reflect.Method;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KarateBackend implements Backend {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateBackend.class);
    
    private final JavaBackend backend;
    private final KarateObjectFactory objectFactory;
    private Glue glue;
    
    public KarateBackend(String featureDir, ClassLoader fileClassLoader, String env) {
        ClassFinder classFinder = new KarateClassFinder(fileClassLoader);
        objectFactory = new KarateObjectFactory(featureDir, fileClassLoader, env);
        backend = new JavaBackend(objectFactory, classFinder);        
    }

    public StepDefs getStepDefs() {
        return objectFactory.getStepDefs();
    }

    public Glue getGlue() {
        return glue;
    }        

    @Override
    public void loadGlue(Glue glue, List<String> NOT_USED) {        
        logger.trace("load glue");
        this.glue = glue;
        Class glueCodeClass = StepDefs.class;
        for (Method method : glueCodeClass.getMethods()) {
            backend.loadGlue(glue, method, glueCodeClass);
        }         
    }

    @Override
    public void setUnreportedStepExecutor(UnreportedStepExecutor executor) {
        logger.trace("set unreported step executor");
        backend.setUnreportedStepExecutor(executor);
    }

    @Override
    public void buildWorld() {
        logger.trace("build world");
        backend.buildWorld();
    }

    @Override
    public void disposeWorld() {
        logger.trace("dispose world");
        backend.disposeWorld();
    }

    @Override
    public String getSnippet(Step step, FunctionNameGenerator functionNameGenerator) {
        logger.debug("get snippet");
        return backend.getSnippet(step, functionNameGenerator);
    }
    
}
