package com.intuit.karate.junit4.files;

import com.intuit.karate.Resource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.Runner;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
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
    
    private ClassLoader getJarClassLoader() throws Exception {
        File jar = new File("../karate-core/src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});        
    }

    @Test
    public void testRunningFromJarFile() throws Exception {
        ClassLoader cl = getJarClassLoader();
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
        Map<String, Object> map = Runner.runFeature(feature, null, false);
        assertEquals(true, map.get("success"));
    }
    
    @Test
    public void testFileUtilsForJarFile() throws Exception {
        File file = new File("src/test/java/common.feature");
        assertTrue(FileUtils.isFile(file.toPath()));
        ClassLoader cl = getJarClassLoader();
        Class main = cl.loadClass("demo.jar1.Main");
        Path path = FileUtils.getPathContaining(main);
        assertFalse(FileUtils.isFile(path));
        String relativePath = FileUtils.toRelativeClassPath(path, cl);
        assertEquals("classpath:demo/jar1", relativePath);
        path = FileUtils.fromRelativeClassPath(relativePath, cl);
        assertEquals(path.toString(), "/demo/jar1");
    }

}
