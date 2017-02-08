package com.intuit.karate.cucumber;

import cucumber.api.java.en.And;
import cucumber.api.java.en.But;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.ClassFinder;
import java.util.Arrays;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class KarateClassFinder implements ClassFinder {
    
    private static final Logger logger = LoggerFactory.getLogger(KarateClassFinder.class);
    
    private final ClassLoader classLoader;
    
    public KarateClassFinder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public <T> Collection<Class<? extends T>> getDescendants(Class<T> parentType, String packageName) {
        logger.trace("get descendants: {}, {}", parentType, packageName);
        Class[] classes = new Class[]{And.class, But.class, Given.class, Then.class, When.class};
        return Arrays.asList(classes);
    }

    @Override
    public <T> Class<? extends T> loadClass(String className) throws ClassNotFoundException {
        logger.trace("loading class: {}", className);
        return (Class<? extends T>) classLoader.loadClass(className);
    }
    
}
