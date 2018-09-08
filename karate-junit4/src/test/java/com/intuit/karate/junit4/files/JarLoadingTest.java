package com.intuit.karate.junit4.files;

import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.cucumber.CucumberRunner;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class JarLoadingTest {

    private static final Logger logger = LoggerFactory.getLogger(JarLoadingTest.class);

    @Test
    public void testRunningFromJarFile() throws Exception {
        File jar = new File("../karate-core/src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toURI().toURL()});
        Class main = cl.loadClass("demo.jar1.Main");
        Method meth = main.getMethod("hello");
        Object result = meth.invoke(null);
        assertEquals("hello world", result);
        List<Resource> list = FileUtils.scanForFeatureFiles(Collections.singletonList("classpath:demo"), cl);
        assertEquals(4, list.size());
        logger.debug("resources: {}", list);
        list = FileUtils.scanForFeatureFiles(Collections.singletonList("classpath:demo/jar1/caller.feature"), cl);
        assertEquals(1, list.size());
        Resource resource = list.get(0);
        Feature feature = FeatureParser.parse(resource);
        Map<String, Object> map = CucumberRunner.runFeature(feature, null, false);
        assertEquals(true, map.get("success"));
    }

}
