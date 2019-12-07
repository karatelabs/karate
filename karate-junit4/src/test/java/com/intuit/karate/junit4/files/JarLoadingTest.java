package com.intuit.karate.junit4.files;

import com.intuit.karate.CallContext;
import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.Runner;
import com.intuit.karate.core.FeatureContext;
import com.intuit.karate.core.ScenarioContext;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    private static ClassLoader getJarClassLoader1() throws Exception {
        File jar = new File("../karate-core/src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});
    }

    @Test
    public void testRunningFromJarFile() throws Exception {
        ClassLoader cl = getJarClassLoader1();
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
        assertTrue(FileUtils.isJarPath(resource.getPath().toUri()));
        Path path = FileUtils.fromRelativeClassPath("classpath:demo/jar1/caller.feature", cl);
        String relativePath = FileUtils.toRelativeClassPath(path, cl);
        assertEquals("classpath:demo/jar1/caller.feature", relativePath);
        Feature feature = FeatureParser.parse(resource);
        Thread.currentThread().setContextClassLoader(cl);
        Map<String, Object> map = Runner.runFeature(feature, null, false);
        assertEquals(true, map.get("success"));
    }

    @Test
    public void testFileUtilsForJarFile() throws Exception {
        File file = new File("src/test/java/common.feature");
        assertTrue(!FileUtils.isJarPath(file.toPath().toUri()));
        ClassLoader cl = getJarClassLoader1();
        Class main = cl.loadClass("demo.jar1.Main");
        Path path = FileUtils.getPathContaining(main);
        assertTrue(FileUtils.isJarPath(path.toUri()));
        String relativePath = FileUtils.toRelativeClassPath(path, cl);
        assertEquals("classpath:", relativePath); // TODO doesn't matter but fix in future if possible
        path = FileUtils.fromRelativeClassPath("classpath:demo/jar1", cl);
        assertEquals(path.toString(), "/demo/jar1");
    }
    
    private static ClassLoader getJarClassLoader2() throws Exception {
        File jar = new File("../karate-core/src/test/resources/karate-test2.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});
    }    
    
    private ScenarioContext getContext() throws Exception {
        Path featureDir = FileUtils.getPathContaining(getClass());
        FeatureContext featureContext = FeatureContext.forWorkingDir("dev", featureDir.toFile());
        CallContext callContext = new CallContext(null, true);
        return new ScenarioContext(featureContext, callContext, getJarClassLoader2(), null, null);
    }    

    @Test
    public void testClassPathJarResource() throws Exception {
        String relativePath = "classpath:example/dependency.feature";
        Resource resource = new Resource(getContext(), relativePath);
        String temp = resource.getAsString();
        logger.debug("string: {}", temp);
    }

    @Test
    public void testUsingKarateBase() throws Exception {
        String relativePath = "classpath:demo/jar1/caller.feature";
        ClassLoader cl = getJarClassLoader1();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> list = new ArrayList();
        for (int i = 0; i < 10; i++) {
            list.add(() -> {
                Path path = FileUtils.fromRelativeClassPath(relativePath, cl);
                logger.debug("path: {}", path);
                Resource resource = new Resource(path, relativePath, -1);
                Feature feature = FeatureParser.parse(resource);
                Map<String, Object> map = Runner.runFeature(feature, null, true);
                Boolean result = (Boolean) map.get("success");
                logger.debug("done: {}", result);
                return result;
            });
        }
        List<Future<Boolean>> futures = executor.invokeAll(list);
        for (Future<Boolean> f : futures) {
            assertTrue(f.get());
        }
        executor.shutdownNow();
    }

}
