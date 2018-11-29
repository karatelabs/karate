package com.intuit.karate;

import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.exception.KarateException;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FileUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger(FileUtilsTest.class);

    @Test
    public void testWindowsFileNames() {
        String path = "com/intuit/karate/cucumber/scenario.feature";
        String fixed = FileUtils.toPackageQualifiedName(path);
        assertEquals("com.intuit.karate.cucumber.scenario", fixed);
    }

    @Test
    public void testRenameZeroLengthFile() {
        long time = System.currentTimeMillis();
        String name = "target/" + time + ".json";
        FileUtils.writeToFile(new File(name), "");
        FileUtils.renameFileIfZeroBytes(name);
        File file = new File(name + ".fail");
        assertTrue(file.exists());
    }

    @Test
    public void testScanFile() {
        String relativePath = "classpath:com/intuit/karate/ui/test.feature";
        ClassLoader cl = getClass().getClassLoader();
        List<Resource> files = FileUtils.scanForFeatureFilesOnClassPath(cl);
        boolean found = false;
        for (Resource file : files) {
            String actualPath = file.getRelativePath().replace('\\', '/');
            if (actualPath.equals(relativePath)) {
                String temp = FileUtils.toRelativeClassPath(file.getPath(), cl);
                assertEquals(temp, actualPath);
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testScanFilePath() {
        String relativePath = "classpath:com/intuit/karate/ui";
        List<Resource> files = FileUtils.scanForFeatureFiles(true, relativePath, getClass().getClassLoader());
        assertEquals(2, files.size());
    }

    @Test
    public void testRelativePathForClass() {
        assertEquals("classpath:com/intuit/karate", FileUtils.toRelativeClassPath(getClass()));
    }

    @Test
    public void testGetAllClasspaths() {
        List<URL> urls = FileUtils.getAllClassPathUrls(getClass().getClassLoader());
        for (URL url : urls) {
            logger.debug("url: {}", url);
        }
    }

    private static ClassLoader getJarClassLoader() throws Exception {
        File jar = new File("src/test/resources/karate-test.jar");
        assertTrue(jar.exists());
        return new URLClassLoader(new URL[]{jar.toURI().toURL()});
    }

    @Test
    public void testUsingKarateBase() throws Exception {
        String relativePath = "classpath:demo/jar1/caller.feature";
        ClassLoader cl = getJarClassLoader();
        Path path = FileUtils.fromRelativeClassPath(relativePath, cl);
        Resource resource = new Resource(path, relativePath);
        Feature feature = FeatureParser.parse(resource);
        try {
            Map<String, Object> map = Runner.runFeature(feature, null, true);
            fail("we should not have reached here");
        } catch (Exception e) {
            assertTrue(e instanceof KarateException);
        }
    }

}
