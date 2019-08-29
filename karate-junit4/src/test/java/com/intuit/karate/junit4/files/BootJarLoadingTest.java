package com.intuit.karate.junit4.files;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

import com.intuit.karate.Resource;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import static java.util.stream.Collectors.*;
import static org.junit.Assert.*;
import static org.springframework.core.io.support.ResourcePatternResolver.*;

/**
 * Tests on Karate's runner applied on resources bundled in a bootJar.
 */
public class BootJarLoadingTest {

    private static ClassLoader classLoader;
    
    @BeforeClass
    public static void beforeAll() throws Exception {
        classLoader = getJarClassLoader();
    }

    // @Test
    public void testRunningFromBootJar() {
        // mimics how a Spring Boot application sets its class loader into the thread's context
        Thread.currentThread().setContextClassLoader(classLoader);
        SpringBootResourceLoader springBootResourceLoader = new SpringBootResourceLoader(classLoader, "com/karate/jartest");
        List<Resource> resources = springBootResourceLoader.asKarateResources();
        assertEquals(6, resources.size());
        Results results = Runner.parallel(resources, 1, "target/surefire-reports");
        assertEquals(6, results.getFeatureCount());
        assertEquals(6, results.getPassCount());
    }

    private static ClassLoader getJarClassLoader() throws Exception {
        File jar = new File("../karate-core/src/test/resources/karate-bootjar-test.jar");
        assertTrue(jar.exists());
        return new IntegrationTestJarLauncher(new JarFileArchive(jar)).createClassLoader();
    }

    /**
     * Custom {@link JarLauncher} used for retrieving a resource in a bootJar.
     */
    static class IntegrationTestJarLauncher extends JarLauncher {

        IntegrationTestJarLauncher(Archive archive) {
            super(archive);
        }

        ClassLoader createClassLoader() throws Exception {
            return createClassLoader(getClassPathArchives());
        }
    }

    /**
     * Loads feature files using the provided ClassLoader
     */
    private static class SpringBootResourceLoader {

        private final org.springframework.core.io.Resource[] resources;

        SpringBootResourceLoader(ClassLoader cl, String packagePath) {
            String locationPattern = CLASSPATH_ALL_URL_PREFIX + "**/" + packagePath + "/**/*.feature";
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
            try {
                resources = resolver.getResources(locationPattern);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        List<Resource> asKarateResources() {
            return Arrays.stream(resources)
                    .map(SpringBootResourceLoader::toSpringBootResource)
                    .collect(toList());
        }

        private static SpringBootResource toSpringBootResource(org.springframework.core.io.Resource resource) {
            try {
                return new SpringBootResource(resource);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * An extension of Karate's {@link Resource} handling a Spring Boot's resource.
     */
    private static class SpringBootResource extends Resource {

        private static final String BOOT_INF_CLASS_DIRECTORY = "BOOT-INF/classes!/";

        SpringBootResource(org.springframework.core.io.Resource resource) throws IOException {
            super(resource.getURL());
        }

        private static String getBootClassSubstring(String path) {
            // The filePath will always contain a Spring-Boot-specific directory structure here,
            // since at this point we always expect to be inside a bundled (a bootJar) Spring Boot application
            return path.substring(path.indexOf(BOOT_INF_CLASS_DIRECTORY) + BOOT_INF_CLASS_DIRECTORY.length());
        }
    }
    
}
