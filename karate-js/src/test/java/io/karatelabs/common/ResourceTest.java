package io.karatelabs.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTest {

    @TempDir
    Path tempDir;

    private File testFile;
    private File subDir;
    private File subDirFile;

    @BeforeEach
    void setUp() {
        // Create test files
        testFile = tempDir.resolve("test.txt").toFile();
        FileUtils.writeToFile(testFile, "Hello World");

        // Create subdirectory with file
        subDir = tempDir.resolve("subdir").toFile();
        subDir.mkdir();
        subDirFile = new File(subDir, "nested.txt");
        FileUtils.writeToFile(subDirFile, "Nested Content");
    }

    @AfterEach
    void tearDown() {
        // TempDir extension handles cleanup
    }

    // ========== UrlResource Tests ==========

    @Test
    void testUrlResourceFromFile() {
        Resource resource = Resource.from(testFile.toPath());

        assertTrue(resource.isFile());
        assertFalse(resource.isInMemory());
        assertFalse(resource.isClassPath());
        assertEquals("Hello World", resource.getText());
        // Removed getFile() assertion;
        assertNotNull(resource.getUri());
        assertNotNull(resource.getPath());
    }

    @Test
    void testUrlResourceRelativePath() {
        Resource resource = Resource.from(testFile.toPath());
        String relativePath = resource.getRelativePath();
        assertNotNull(relativePath);
        // Should not be an absolute path if within working directory
        assertFalse(relativePath.contains(FileUtils.WORKING_DIR.getAbsolutePath()));
    }

    @Test
    void testUrlResourceGetLine() {
        File multiLineFile = tempDir.resolve("multiline.txt").toFile();
        FileUtils.writeToFile(multiLineFile, "Line 1\nLine 2\nLine 3");

        Resource resource = Resource.from(multiLineFile.toPath());
        assertEquals("Line 1", resource.getLine(0));
        assertEquals("Line 2", resource.getLine(1));
        assertEquals("Line 3", resource.getLine(2));
    }

    @Test
    void testUrlResourceResolve() {
        Resource resource = Resource.from(subDirFile.toPath());
        Resource resolved = resource.resolve("sibling.txt");

        assertNotNull(resolved);
        assertTrue(resolved.isFile());
    }

    @Test
    void testUrlResourceGetExtension() {
        Resource resource = Resource.from(testFile.toPath());
        assertEquals("txt", resource.getExtension());

        File noExtFile = tempDir.resolve("noext").toFile();
        FileUtils.writeToFile(noExtFile, "content");
        Resource noExtResource = Resource.from(noExtFile.toPath());
        assertEquals("", noExtResource.getExtension());
    }

    @Test
    void testUrlResourceGetFileNameWithoutExtension() {
        Resource resource = Resource.from(testFile.toPath());
        String path = resource.getFileNameWithoutExtension();
        assertTrue(path.endsWith("test"));
        assertFalse(path.endsWith(".txt"));
    }

    @Test
    void testUrlResourceGetLastModified() {
        Resource resource = Resource.from(testFile.toPath());
        long lastModified = resource.getLastModified();
        assertTrue(lastModified > 0);
    }

    // ========== MemoryResource Tests ==========

    @Test
    void testMemoryResourceFromText() {
        Resource resource = Resource.text("Memory Content");

        assertFalse(resource.isFile());
        assertTrue(resource.isInMemory());
        assertFalse(resource.isClassPath());
        assertEquals("Memory Content", resource.getText());
        // Removed getFile() assertion;
        assertNull(resource.getUri());
        assertNull(resource.getPath());
    }

    @Test
    void testMemoryResourceGetLine() {
        Resource resource = Resource.text("First\nSecond\nThird");
        assertEquals("First", resource.getLine(0));
        assertEquals("Second", resource.getLine(1));
        assertEquals("Third", resource.getLine(2));
    }

    @Test
    void testMemoryResourceResolve() {
        Resource resource = Resource.text("content");
        Resource resolved = resource.resolve("test.txt");

        assertNotNull(resolved);
        assertTrue(resolved.isFile());
    }

    @Test
    void testMemoryResourceResolveClasspathPrefix() {
        // MemoryResource.resolve() should handle classpath: prefix
        Resource resource = Resource.text("Feature: test");

        // This should not prepend temp directory to the classpath path
        // Instead, it should delegate to Resource.path() for classpath: prefixed paths
        assertThrows(RuntimeException.class, () -> {
            // Should attempt to load from classpath, not file system
            resource.resolve("classpath:nonexistent/test.feature");
        });
    }

    @Test
    void testMemoryResourceResolveFilePrefix() {
        // MemoryResource.resolve() should handle file: prefix
        Resource resource = Resource.text("Feature: test");

        // file: prefix should be handled by Resource.path()
        Resource resolved = resource.resolve("file:" + tempDir.resolve("test.txt").toAbsolutePath());
        assertNotNull(resolved);
        assertTrue(resolved.isFile());
    }

    @Test
    void testMemoryResourceGetStream() throws IOException {
        Resource resource = Resource.text("Stream Test");
        String content = FileUtils.toString(resource.getStream());
        assertEquals("Stream Test", content);
    }

    // ========== Path API Tests ==========

    @Test
    void testGetPath() {
        Resource fileResource = Resource.from(testFile.toPath());
        Path path = fileResource.getPath();
        assertNotNull(path);
        assertEquals(testFile.toPath().toAbsolutePath().normalize(), path);

        Resource memoryResource = Resource.text("test");
        assertNull(memoryResource.getPath());
    }

    @Test
    void testGetRelativePathTo() {
        Resource resource1 = Resource.from(testFile.toPath());
        Resource resource2 = Resource.from(subDirFile.toPath());

        String relativePath = resource1.getRelativePathTo(resource2);
        assertNotNull(relativePath);
        assertTrue(relativePath.contains("subdir"));
    }

    @Test
    void testGetRelativePathToSameDirectory() {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        FileUtils.writeToFile(file1, "content1");
        FileUtils.writeToFile(file2, "content2");

        Resource resource1 = Resource.from(file1.toPath());
        Resource resource2 = Resource.from(file2.toPath());

        String relativePath = resource1.getRelativePathTo(resource2);
        assertNotNull(relativePath);
        // When relativizing from a file to its sibling, the path goes up one level
        // then to the target file
        assertTrue(relativePath.endsWith("file2.txt"));
    }

    @Test
    void testGetRelativePathToWithMemoryResource() {
        Resource fileResource = Resource.from(testFile.toPath());
        Resource memoryResource = Resource.text("test");

        // Cannot compute relative path with memory resource
        assertNull(fileResource.getRelativePathTo(memoryResource));
        assertNull(memoryResource.getRelativePathTo(fileResource));
    }

    @Test
    void testRelativePathUsesForwardSlashes() {
        // Verify that relative paths always use forward slashes, even on Windows
        Resource resource1 = Resource.from(testFile.toPath());
        Resource resource2 = Resource.from(subDirFile.toPath());

        String relativePath = resource1.getRelativePathTo(resource2);
        assertNotNull(relativePath);

        // Should use forward slashes for cross-platform consistency
        assertFalse(relativePath.contains("\\"), "Relative path should not contain backslashes");
        assertTrue(relativePath.contains("/") || !relativePath.contains("\\"),
                "Relative path should use forward slashes for directories");
    }

    @Test
    void testResourceRelativePathUsesForwardSlashes() {
        // Verify that Resource.getRelativePath() uses forward slashes
        File nestedFile = tempDir.resolve("level1").resolve("level2").resolve("test.txt").toFile();
        FileUtils.writeToFile(nestedFile, "nested content");

        Resource resource = Resource.from(nestedFile.toPath());
        String relativePath = resource.getRelativePath();

        assertNotNull(relativePath);
        // Should not contain backslashes on any platform
        assertFalse(relativePath.contains("\\"),
                "Resource relative path should not contain backslashes");
    }

    // ========== Windows Cross-Drive Tests ==========

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testWindowsCrossDriveRelativePath() {
        // Simulate cross-drive scenario
        // This test only runs on Windows where drive letters exist
        File cDriveFile = new File("C:\\temp\\test.txt");
        File dDriveFile = new File("D:\\data\\file.txt");

        // These files don't need to exist for the path logic to work
        Resource resourceC = Resource.from(cDriveFile.toPath());
        Resource resourceD = Resource.from(dDriveFile.toPath());

        // Cross-drive relativization should fallback to absolute path
        String relativePath = resourceC.getRelativePathTo(resourceD);
        assertNotNull(relativePath, "Should fallback to absolute path for cross-drive");
        assertTrue(relativePath.startsWith("D:/"), "Should return absolute path with forward slashes");
        assertTrue(relativePath.contains("data/file.txt"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testWindowsCrossDriveResourceCreation() {
        // Test that creating a Resource with a file on a different drive
        // doesn't throw an exception (the bug we fixed)
        File dDriveFile = new File("D:\\test\\file.txt");

        assertDoesNotThrow(() -> {
            Resource resource = Resource.from(dDriveFile.toPath());
            assertNotNull(resource);
            assertNotNull(resource.getRelativePath());
        }, "Should not throw exception when creating resource on different drive");
    }

    // ========== Static Utility Methods Tests ==========

    @Test
    void testRemovePrefix() {
        assertEquals("path/to/file", Resource.removePrefix("classpath:path/to/file"));
        assertEquals("path/to/file", Resource.removePrefix("file:path/to/file"));
        assertEquals("path/to/file", Resource.removePrefix("path/to/file"));
    }

    @Test
    void testGetParentPath() {
        assertEquals("path/to/", Resource.getParentPath("path/to/file.txt"));
        assertEquals("", Resource.getParentPath("file.txt"));
        assertEquals("a/b/", Resource.getParentPath("a/b/c"));
    }

    @Test
    void testResourcePath() {
        File file = tempDir.resolve("pathtest.txt").toFile();
        FileUtils.writeToFile(file, "content");

        Resource resource = Resource.path(file.getAbsolutePath());
        assertNotNull(resource);
        assertTrue(resource.isFile());
        assertEquals("content", resource.getText());
    }

    @Test
    void testResourcePathClasspath() {
        // This test assumes there's a resource on the classpath
        // For a real test, you'd need an actual classpath resource
        assertThrows(RuntimeException.class, () -> {
            Resource.path("classpath:nonexistent/file.txt");
        });
    }

    // ========== Integration Tests ==========

    @Test
    void testGetPackageQualifiedName() {
        File featureFile = tempDir.resolve("test.feature").toFile();
        FileUtils.writeToFile(featureFile, "Feature: test");

        Resource resource = Resource.from(featureFile.toPath());
        String pqn = resource.getPackageQualifiedName();

        assertNotNull(pqn);
        assertFalse(pqn.contains(".feature"));
        assertFalse(pqn.startsWith("/"));
    }

    @Test
    void testGetPrefixedPath() {
        Resource fileResource = Resource.from(testFile.toPath());
        String prefixed = fileResource.getPrefixedPath();
        assertNotNull(prefixed);
        assertFalse(prefixed.startsWith("classpath:"));

        // For classpath resources, would need actual classpath test resources
    }

    @Test
    void testResourceToString() {
        Resource resource = Resource.from(testFile.toPath());
        String str = resource.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
    }

    // ========== NIO FileUtils Tests ==========

    @Test
    void testFileUtilsCopy() throws IOException {
        File source = tempDir.resolve("source.txt").toFile();
        File dest = tempDir.resolve("dest.txt").toFile();

        FileUtils.writeToFile(source, "Copy Test");
        FileUtils.copy(source, dest);

        assertTrue(dest.exists());
        assertEquals("Copy Test", FileUtils.toString(dest));
    }

    @Test
    void testFileUtilsToBytes() {
        File file = tempDir.resolve("bytes.txt").toFile();
        FileUtils.writeToFile(file, "Bytes Test");

        byte[] bytes = FileUtils.toBytes(file);
        assertNotNull(bytes);
        assertEquals("Bytes Test", new String(bytes));
    }

    @Test
    void testFileUtilsWriteToFileCreatesDirectories() {
        File nestedFile = tempDir.resolve("a/b/c/file.txt").toFile();
        assertFalse(nestedFile.getParentFile().exists());

        FileUtils.writeToFile(nestedFile, "Nested");

        assertTrue(nestedFile.exists());
        assertEquals("Nested", FileUtils.toString(nestedFile));
    }

    @Test
    void testResourceStreamMultipleReads() throws IOException {
        Resource resource = Resource.from(testFile.toPath());

        // Should be able to call getStream multiple times
        String content1 = FileUtils.toString(resource.getStream());
        String content2 = FileUtils.toString(resource.getStream());

        assertEquals("Hello World", content1);
        assertEquals("Hello World", content2);
    }

    // ========== Custom Root Tests ==========

    @Test
    void testResourceWithCustomRoot() throws IOException {
        // Create a custom root different from working directory
        Path customRoot = tempDir.resolve("project-root");
        Files.createDirectories(customRoot);

        // Create a file inside a subdirectory of custom root
        Path subdir = customRoot.resolve("src/main");
        Files.createDirectories(subdir);
        Path file = subdir.resolve("Test.java");
        Files.writeString(file, "public class Test {}");

        // Create resource with custom root
        Resource resource = Resource.from(file, customRoot);

        // Relative path should be from custom root, not working directory
        String relativePath = resource.getRelativePath();
        assertNotNull(relativePath);
        assertTrue(relativePath.contains("src/main/Test.java") || relativePath.endsWith("Test.java"));
        assertFalse(relativePath.contains("\\"), "Should use forward slashes");
    }

    @Test
    void testGetRelativePathFrom() throws IOException {
        Path customRoot = tempDir.resolve("webapp");
        Path webFile = customRoot.resolve("static/index.html");
        Files.createDirectories(webFile.getParent());
        Files.writeString(webFile, "<html></html>");

        Resource resource = Resource.from(webFile);
        String relativePath = resource.getRelativePathFrom(customRoot);

        assertNotNull(relativePath);
        assertTrue(relativePath.endsWith("static/index.html") || relativePath.contains("static"));
        assertFalse(relativePath.contains("\\"));
    }

    @Test
    void testGetRelativePathFromNullRoot() {
        // Null root should return null (not a valid operation)
        Resource resource = Resource.from(testFile.toPath());
        assertNull(resource.getRelativePathFrom(null));
    }

    @Test
    void testGetRelativePathFromFallbackToAbsolute() throws IOException {
        // When relativization fails, should fallback to absolute path
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content");

        Resource resource = Resource.from(file);

        // Try to relativize from a completely different root
        // On same drive, this should still compute a relative path
        // But the important thing is it never returns null for valid file resources
        Path differentRoot = Path.of(System.getProperty("user.home"));
        String result = resource.getRelativePathFrom(differentRoot);

        assertNotNull(result, "Should never return null for file resources with valid root");
        assertFalse(result.contains("\\"), "Should use forward slashes");
    }

    @Test
    void testGetRelativePathToFallbackToAbsolute() throws IOException {
        // When relativization fails, should fallback to absolute path
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        Resource resource1 = Resource.from(file1);
        Resource resource2 = Resource.from(file2);

        String result = resource1.getRelativePathTo(resource2);

        // Should always return a usable path
        assertNotNull(result, "Should never return null for file-to-file relativization");
        assertFalse(result.contains("\\"), "Should use forward slashes");
    }

    // ========== PathResource Tests ==========

    @Test
    void testPathResourceCreation() {
        Path path = testFile.toPath();
        Resource resource = Resource.from(path);

        assertNotNull(resource);
        assertTrue(resource.isFile());
        assertFalse(resource.isInMemory());
        assertEquals("Hello World", resource.getText());
        assertEquals(path.toAbsolutePath().normalize(), resource.getPath());
    }

    @Test
    void testPathResourceWithCustomRoot() throws IOException {
        Path projectRoot = tempDir.resolve("myproject");
        Files.createDirectories(projectRoot);

        Path srcFile = projectRoot.resolve("src/main/App.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, "public class App {}");

        Resource resource = Resource.from(srcFile, projectRoot);

        String relativePath = resource.getRelativePath();
        assertNotNull(relativePath);
        assertTrue(relativePath.contains("src/main/App.java") || relativePath.endsWith("App.java"));
        assertFalse(relativePath.contains("\\"));
    }

    @Test
    void testPathResourceResolve() throws IOException {
        Path dir = tempDir.resolve("docs");
        Files.createDirectories(dir);
        Path readme = dir.resolve("README.md");
        Files.writeString(readme, "# README");

        Resource resource = Resource.from(readme);
        Resource resolved = resource.resolve("CHANGELOG.md");

        assertNotNull(resolved);
        assertTrue(resolved.getRelativePath().contains("CHANGELOG.md"));
    }

    @Test
    void testPathResourceResolveClasspathPrefix() throws IOException {
        // PathResource.resolve() should handle classpath: prefix
        Path dir = tempDir.resolve("src");
        Files.createDirectories(dir);
        Path file = dir.resolve("Main.java");
        Files.writeString(file, "class Main {}");

        Resource resource = Resource.from(file);

        // Should attempt to load from classpath, not resolve as relative path
        assertThrows(RuntimeException.class, () -> {
            resource.resolve("classpath:nonexistent/test.feature");
        });
    }

    @Test
    void testPathResourceResolveFilePrefix() throws IOException {
        // PathResource.resolve() should handle file: prefix
        Path dir = tempDir.resolve("project");
        Files.createDirectories(dir);
        Path file1 = dir.resolve("file1.txt");
        Path file2 = dir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        Resource resource = Resource.from(file1);

        // file: prefix should work with absolute paths
        Resource resolved = resource.resolve("file:" + file2.toAbsolutePath());
        assertNotNull(resolved);
        assertTrue(resolved.isFile());
        assertEquals("content2", resolved.getText());
    }

    @Test
    void testPathResourceGetStream() throws IOException {
        Path path = testFile.toPath();
        Resource resource = Resource.from(path);

        try (InputStream is = resource.getStream()) {
            String content = FileUtils.toString(is);
            assertEquals("Hello World", content);
        }
    }

    @Test
    void testPathResourceGetLine() throws IOException {
        Path multiLine = tempDir.resolve("multi.txt");
        Files.writeString(multiLine, "First\nSecond\nThird");

        Resource resource = Resource.from(multiLine);
        assertEquals("First", resource.getLine(0));
        assertEquals("Second", resource.getLine(1));
        assertEquals("Third", resource.getLine(2));
    }

    @Test
    void testPathResourceLastModified() {
        Path path = testFile.toPath();
        Resource resource = Resource.from(path);

        long lastModified = resource.getLastModified();
        assertTrue(lastModified > 0);
    }

    // ========== getParent() Tests ==========

    @Test
    void testGetParentBasic() throws IOException {
        // Create nested structure: tempDir/level1/level2/file.txt
        Path level1 = tempDir.resolve("level1");
        Path level2 = level1.resolve("level2");
        Files.createDirectories(level2);
        Path file = level2.resolve("file.txt");
        Files.writeString(file, "content");

        Resource resource = Resource.from(file);
        Resource parent = resource.getParent();

        assertNotNull(parent);
        assertEquals(level2, parent.getPath());
        assertTrue(parent.isFile());
    }

    @Test
    void testGetParentChain() throws IOException {
        // Create nested structure and navigate up multiple levels
        Path level1 = tempDir.resolve("a");
        Path level2 = level1.resolve("b");
        Path level3 = level2.resolve("c");
        Files.createDirectories(level3);
        Path file = level3.resolve("file.txt");
        Files.writeString(file, "content");

        Resource resource = Resource.from(file);

        // file.txt -> c
        Resource parent1 = resource.getParent();
        assertNotNull(parent1);
        assertEquals(level3, parent1.getPath());

        // c -> b
        Resource parent2 = parent1.getParent();
        assertNotNull(parent2);
        assertEquals(level2, parent2.getPath());

        // b -> a
        Resource parent3 = parent2.getParent();
        assertNotNull(parent3);
        assertEquals(level1, parent3.getPath());
    }

    @Test
    void testGetParentPreservesRoot() throws IOException {
        // Verify that parent preserves the custom root
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        Path srcDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path javaFile = srcDir.resolve("App.java");
        Files.writeString(javaFile, "class App {}");

        // Create resource with custom root
        Resource resource = Resource.from(javaFile, projectRoot);

        // Get parent and verify it also uses the custom root
        Resource parent = resource.getParent();
        assertNotNull(parent);

        // Parent's relative path should also be computed from projectRoot
        String parentRelativePath = parent.getRelativePath();
        assertNotNull(parentRelativePath);
        assertTrue(parentRelativePath.contains("src/main/java") || parentRelativePath.contains("java"));
    }

    @Test
    void testGetParentOnRoot() {
        // Getting parent of root should return null
        Path root = Path.of("/").toAbsolutePath();
        Resource resource = Resource.from(root);

        Resource parent = resource.getParent();
        assertNull(parent);
    }

    @Test
    void testGetParentMemoryResource() {
        // MemoryResource should return null for getParent()
        Resource resource = Resource.text("in-memory content");

        Resource parent = resource.getParent();
        assertNull(parent);
    }

    @Test
    void testGetParentResolveChain() throws IOException {
        // Test that parent and resolve work together correctly
        Path dir = tempDir.resolve("workspace");
        Files.createDirectories(dir);
        Path file1 = dir.resolve("file1.txt");
        Path file2 = dir.resolve("file2.txt");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");

        Resource resource1 = Resource.from(file1);

        // Navigate to parent (workspace dir) then resolve to sibling
        Resource parent = resource1.getParent();
        assertNotNull(parent);

        Resource sibling = parent.resolve("file2.txt");
        assertTrue(sibling.getRelativePath().contains("file2.txt"));
    }

    // ========== getRoot() Tests ==========

    @Test
    void testGetRootPathResource() throws IOException {
        Path customRoot = tempDir.resolve("myroot");
        Files.createDirectories(customRoot);
        Path file = customRoot.resolve("file.txt");
        Files.writeString(file, "content");

        Resource resource = Resource.from(file, customRoot);
        Path root = resource.getRoot();

        assertNotNull(root);
        assertEquals(customRoot, root);
    }

    @Test
    void testGetRootPathResourceDefaultsToWorkingDir() {
        Path file = tempDir.resolve("file.txt");
        Resource resource = Resource.from(file);

        Path root = resource.getRoot();
        assertNotNull(root);
        // Should default to working directory
        assertEquals(FileUtils.WORKING_DIR.toPath().toAbsolutePath().normalize(),
                     root.toAbsolutePath().normalize());
    }

    @Test
    void testGetRootMemoryResourceDefaultsToTemp() {
        Resource resource = Resource.text("in-memory");

        Path root = resource.getRoot();
        assertNotNull(root);
        // Should default to system temp
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")), root);
    }

    @Test
    void testGetRootMemoryResourceCustomRoot() {
        Path customRoot = tempDir.resolve("custom");
        Resource resource = Resource.text("in-memory", customRoot);

        Path root = resource.getRoot();
        assertNotNull(root);
        assertEquals(customRoot, root);
    }

    @Test
    void testGetRootPreservedThroughParent() throws IOException {
        Path customRoot = tempDir.resolve("project");
        Files.createDirectories(customRoot);
        Path dir = customRoot.resolve("src/main");
        Files.createDirectories(dir);
        Path file = dir.resolve("App.java");
        Files.writeString(file, "class App {}");

        Resource resource = Resource.from(file, customRoot);
        Resource parent = resource.getParent();

        assertNotNull(parent);
        assertEquals(customRoot, parent.getRoot());
    }

    @Test
    void testGetRootPreservedThroughResolve() throws IOException {
        Path customRoot = tempDir.resolve("webapp");
        Files.createDirectories(customRoot);
        Path dir = customRoot.resolve("static");
        Files.createDirectories(dir);
        Path file = dir.resolve("index.html");
        Files.writeString(file, "<html/>");

        Resource resource = Resource.from(file, customRoot);
        Resource sibling = resource.resolve("styles.css");

        assertNotNull(sibling);
        assertEquals(customRoot, sibling.getRoot());
    }

    // ========== MemoryResource materialize() Tests ==========

    @Test
    void testMaterializeBasic() throws IOException {
        Path targetDir = tempDir.resolve("output");
        Files.createDirectories(targetDir);

        MemoryResource memory = (MemoryResource) Resource.text("Hello World", targetDir);
        PathResource materialized = memory.materialize("test.txt");

        assertNotNull(materialized);
        assertTrue(Files.exists(materialized.getPath()));
        assertEquals("Hello World", materialized.getText());
        assertEquals(targetDir, materialized.getRoot());
    }

    @Test
    void testMaterializeWithNestedPath() throws IOException {
        Path targetDir = tempDir.resolve("project");
        Files.createDirectories(targetDir);

        MemoryResource memory = (MemoryResource) Resource.text("<html/>", targetDir);
        PathResource materialized = memory.materialize("dist/index.html");

        assertNotNull(materialized);
        assertTrue(Files.exists(materialized.getPath()));
        assertEquals("<html/>", materialized.getText());
        assertTrue(materialized.getPath().toString().contains("dist"));
    }

    @Test
    void testMaterializeCreatesDirectories() throws IOException {
        Path targetDir = tempDir.resolve("newproject");
        // Don't create targetDir - materialize should handle it

        MemoryResource memory = (MemoryResource) Resource.text("content", targetDir);
        PathResource materialized = memory.materialize("deep/nested/file.txt");

        assertTrue(Files.exists(materialized.getPath()));
        assertEquals("content", materialized.getText());
    }

    @Test
    void testMaterializePreservesRoot() throws IOException {
        Path projectRoot = tempDir.resolve("myproject");
        Files.createDirectories(projectRoot);

        MemoryResource memory = (MemoryResource) Resource.text("data", projectRoot);
        PathResource materialized = memory.materialize("output.txt");

        assertEquals(projectRoot, materialized.getRoot());
        assertEquals("data", materialized.getText());
    }

    @Test
    void testMemoryResourceResolveUsesRoot() {
        Path customRoot = tempDir.resolve("workspace");
        Resource memory = Resource.text("in-memory", customRoot);

        Resource resolved = memory.resolve("sibling.txt");

        assertTrue(resolved.isFile());
        assertEquals(customRoot, resolved.getRoot());
        assertTrue(resolved.getPath().toString().contains("sibling.txt"));
    }

    @Test
    void testMemoryResourceWorkflow() throws IOException {
        // Real-world workflow: generate in memory, resolve relative, materialize both
        Path distDir = tempDir.resolve("dist");
        Files.createDirectories(distDir);

        // Generate HTML in memory with target location
        MemoryResource html = (MemoryResource) Resource.text("<html><link href='style.css'/></html>", distDir);

        // Resolve CSS relative to HTML's future location
        Resource css = html.resolve("style.css");
        assertEquals(distDir, css.getRoot());

        // Materialize HTML to disk
        PathResource savedHtml = html.materialize("index.html");
        assertTrue(Files.exists(savedHtml.getPath()));
        assertEquals(distDir, savedHtml.getRoot());

        // CSS would resolve to correct location
        assertTrue(savedHtml.getPath().getParent().toString().contains("dist"));
    }

    // ========== ClassLoader Tests ==========

    @Test
    void testClasspathResourceWithCustomClassLoader() {
        // This test verifies that custom ClassLoader can be provided
        // In real scenario, you'd have actual classpath resources
        ClassLoader cl = getClass().getClassLoader();

        // This should not throw if classpath handling works correctly
        // (though it will throw because the resource doesn't exist)
        assertThrows(RuntimeException.class, () -> {
            Resource.path("classpath:nonexistent/file.txt", cl);
        });
    }

    @Test
    void testClasspathResourceLeadingSlashHandling() {
        // Both forms should work identically
        assertThrows(RuntimeException.class, () -> {
            Resource.path("classpath:/nonexistent/file.txt");
        });

        assertThrows(RuntimeException.class, () -> {
            Resource.path("classpath:nonexistent/file.txt");
        });
    }

    // ========== Resource Type Detection Tests ==========

    @Test
    void testIsLocalFileForRegularFile() {
        // Regular file on local filesystem should return true
        Resource resource = Resource.from(testFile.toPath());

        assertTrue(resource.isLocalFile());
        assertFalse(resource.isJarResource());
        assertFalse(resource.isInMemory());
        assertTrue(resource.isFile());
    }

    @Test
    void testIsInMemoryForMemoryResource() {
        // In-memory resource should return true for isInMemory
        Resource resource = Resource.text("in-memory content");

        assertFalse(resource.isLocalFile());
        assertFalse(resource.isJarResource());
        assertTrue(resource.isInMemory());
        assertFalse(resource.isFile());
    }

    @Test
    void testIsJarResourceForClasspathResource() {
        // Test with an actual classpath resource that exists in a JAR
        // junit-jupiter-api is a dependency, so we can use one of its classes
        try {
            Resource resource = Resource.path("classpath:org/junit/jupiter/api/Test.class");

            assertFalse(resource.isInMemory());
            assertTrue(resource.isFile());

            // This will be true if running from JARs, false if running from IDE with classes directory
            // So we just verify the method works without throwing
            boolean isJar = resource.isJarResource();
            boolean isLocal = resource.isLocalFile();

            // Should be exactly one of JAR or local file
            assertTrue(isJar != isLocal, "Resource should be either JAR or local file, not both");

        } catch (RuntimeException e) {
            // If classpath resource not found, that's okay for this test
            // The important thing is the methods don't throw exceptions
        }
    }

    @Test
    void testResourceTypeDetectionMatrixForLocalFile() {
        // Verify the complete type detection matrix for a local file
        Resource resource = Resource.from(testFile.toPath());

        // Local file characteristics
        assertTrue(resource.isFile());
        assertTrue(resource.isLocalFile());
        assertFalse(resource.isInMemory());
        assertFalse(resource.isJarResource());

        // Should have file:// URI scheme
        assertNotNull(resource.getUri());
        assertEquals("file", resource.getUri().getScheme());
    }

    @Test
    void testResourceTypeDetectionMatrixForMemoryResource() {
        // Verify the complete type detection matrix for in-memory resource
        Resource resource = Resource.text("test");

        // Memory resource characteristics
        assertFalse(resource.isFile());
        assertFalse(resource.isLocalFile());
        assertTrue(resource.isInMemory());
        assertFalse(resource.isJarResource());

        // Should not have URI or Path
        assertNull(resource.getUri());
        assertNull(resource.getPath());
    }

    // ========== URL Support Tests ==========

    @Test
    void testResourceFromFileUrl() throws Exception {
        // Create a Resource from a file:// URL
        java.net.URL url = testFile.toURI().toURL();
        Resource resource = Resource.from(url);

        assertNotNull(resource);
        assertTrue(resource.isFile());
        assertTrue(resource.isLocalFile());
        assertFalse(resource.isInMemory());
        assertEquals("Hello World", resource.getText());
        assertEquals(testFile.toPath().toAbsolutePath().normalize(), resource.getPath());
    }

    @Test
    void testResourceFromUrlWithCustomRoot() throws Exception {
        // Create a Resource from URL with custom root
        Path customRoot = tempDir.resolve("custom");
        Files.createDirectories(customRoot);
        Path file = customRoot.resolve("test.txt");
        Files.writeString(file, "URL test");

        java.net.URL url = file.toUri().toURL();
        Resource resource = Resource.from(url, customRoot);

        assertNotNull(resource);
        assertTrue(resource.isFile());
        assertEquals("URL test", resource.getText());
        assertEquals(customRoot, resource.getRoot());
    }

    @Test
    void testResourceFromHttpUrlWithMockServer() throws Exception {
        // Start a simple HTTP server
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(0), 0);
        String responseContent = "{\"swagger\": \"2.0\", \"info\": {\"title\": \"Test API\"}}";

        server.createContext("/api/spec.json", exchange -> {
            byte[] response = responseContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            java.net.URL url = URI.create("http://localhost:" + port + "/api/spec.json").toURL();

            Resource resource = Resource.from(url);

            assertNotNull(resource);
            assertFalse(resource.isFile());
            assertTrue(resource.isInMemory());
            assertFalse(resource.isClassPath());
            assertEquals(responseContent, resource.getText());

            // UrlResource should preserve the URI
            assertNotNull(resource.getUri());
            assertEquals("http", resource.getUri().getScheme());
            assertTrue(resource.getUri().toString().contains("/api/spec.json"));

            // getSimpleName should return the file name from the URL path
            assertEquals("spec.json", resource.getSimpleName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testResourceFromHttpsUrlHandlesNetworkError() {
        // Test that network errors are wrapped in RuntimeException with good message
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            // Use a URL that will fail to connect (localhost with unused port)
            java.net.URL url = URI.create("http://localhost:59999/nonexistent").toURL();
            Resource.from(url);
        });
        assertTrue(ex.getMessage().contains("Failed to fetch content from URL"));
    }

    @Test
    void testHttpUrlResourceResolve() throws Exception {
        // Start a simple HTTP server
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(0), 0);

        server.createContext("/api/main.json", exchange -> {
            byte[] response = "main".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.createContext("/api/sibling.json", exchange -> {
            byte[] response = "sibling".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            java.net.URL url = URI.create("http://localhost:" + port + "/api/main.json").toURL();

            Resource main = Resource.from(url);
            assertEquals("main", main.getText());

            // Resolve a sibling URL
            Resource sibling = main.resolve("sibling.json");
            assertNotNull(sibling);
            assertEquals("sibling", sibling.getText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testHttpUrlResourceGetRelativePath() throws Exception {
        // Start a simple HTTP server
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(0), 0);

        server.createContext("/path/to/resource.json", exchange -> {
            byte[] response = "{}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            java.net.URL url = URI.create("http://localhost:" + port + "/path/to/resource.json").toURL();

            Resource resource = Resource.from(url);

            // Relative path should be derived from URL path (without leading slash)
            String relativePath = resource.getRelativePath();
            assertEquals("path/to/resource.json", relativePath);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testHttpUrlResourceToString() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(0), 0);

        server.createContext("/test", exchange -> {
            byte[] response = "test".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            java.net.URL url = URI.create("http://localhost:" + port + "/test").toURL();

            Resource resource = Resource.from(url);
            String str = resource.toString();

            // toString should return the original URL
            assertTrue(str.contains("http://localhost:" + port + "/test"));
        } finally {
            server.stop(0);
        }
    }

    // ========== getSimpleName() Tests ==========

    @Test
    void testGetSimpleNameForFile() {
        Resource resource = Resource.from(testFile.toPath());
        assertEquals("test.txt", resource.getSimpleName());
    }

    @Test
    void testGetSimpleNameForNestedFile() {
        Resource resource = Resource.from(subDirFile.toPath());
        assertEquals("nested.txt", resource.getSimpleName());
    }

    @Test
    void testGetSimpleNameForMemoryResource() {
        Resource resource = Resource.text("content");
        // Memory resources have null URI
        assertEquals("", resource.getSimpleName());
    }

    @Test
    void testGetSimpleNameForFileWithoutExtension() throws IOException {
        File noExtFile = tempDir.resolve("noextension").toFile();
        FileUtils.writeToFile(noExtFile, "content");
        Resource resource = Resource.from(noExtFile.toPath());
        assertEquals("noextension", resource.getSimpleName());
    }

    @Test
    void testGetSimpleNameForDeeplyNestedFile() throws IOException {
        Path deepFile = tempDir.resolve("a/b/c/d/deep.json");
        Files.createDirectories(deepFile.getParent());
        Files.writeString(deepFile, "{}");

        Resource resource = Resource.from(deepFile);
        assertEquals("deep.json", resource.getSimpleName());
    }

    // ========== scanClasspath() Tests ==========

    @Test
    void testScanClasspathEmptyDirectory() {
        // Scan a non-existent directory
        java.util.List<Resource> results = Resource.scanClasspath("nonexistent/directory", "feature");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testScanClasspathNormalizesLeadingSlash() {
        // Both should work identically
        java.util.List<Resource> results1 = Resource.scanClasspath("nonexistent", "txt");
        java.util.List<Resource> results2 = Resource.scanClasspath("/nonexistent", "txt");

        assertEquals(results1.size(), results2.size());
    }

    @Test
    void testScanClasspathNormalizesTrailingSlash() {
        // Both should work identically
        java.util.List<Resource> results1 = Resource.scanClasspath("nonexistent", "txt");
        java.util.List<Resource> results2 = Resource.scanClasspath("nonexistent/", "txt");

        assertEquals(results1.size(), results2.size());
    }

    @Test
    void testScanClasspathWithCustomClassLoader() {
        // Scan with explicit class loader
        ClassLoader cl = getClass().getClassLoader();
        java.util.List<Resource> results = Resource.scanClasspath("nonexistent", "feature", cl);

        assertNotNull(results);
        // Non-existent directory should return empty list
        assertTrue(results.isEmpty());
    }

    // ========== JAR Resource Scanning Tests ==========

    /**
     * Helper to create a test JAR with feature files.
     */
    private Path createTestJar(String jarName, String... featurePaths) throws IOException {
        Path jarPath = tempDir.resolve(jarName);

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String featurePath : featurePaths) {
                // Create directory entries for parent paths
                String[] parts = featurePath.split("/");
                StringBuilder dirPath = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    dirPath.append(parts[i]).append("/");
                    JarEntry dirEntry = new JarEntry(dirPath.toString());
                    try {
                        jos.putNextEntry(dirEntry);
                        jos.closeEntry();
                    } catch (java.util.zip.ZipException e) {
                        // Directory entry already exists, ignore
                    }
                }

                // Create the feature file entry
                JarEntry entry = new JarEntry(featurePath);
                jos.putNextEntry(entry);

                // Write minimal feature content
                String featureName = parts[parts.length - 1].replace(".feature", "");
                String content = String.format("""
                    Feature: %s

                    Scenario: Test scenario
                    * def x = 1
                    * match x == 1
                    """, featureName);
                jos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }

        return jarPath;
    }

    @Test
    void testScanClasspathFromJar() throws Exception {
        // Create a JAR with feature files
        Path jarPath = createTestJar("test-features.jar",
                "features/users.feature",
                "features/orders.feature",
                "features/nested/deep.feature"
        );

        // Create a URLClassLoader with our test JAR
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null  // no parent - isolated classloader
        )) {
            // Scan the features directory
            List<Resource> results = Resource.scanClasspath("features", "feature", classLoader);

            assertNotNull(results);
            assertEquals(3, results.size(), "Should find 3 feature files in JAR");

            // Verify all features were found
            List<String> paths = results.stream()
                    .map(Resource::getRelativePath)
                    .toList();

            assertTrue(paths.stream().anyMatch(p -> p.contains("users.feature")),
                    "Should find users.feature");
            assertTrue(paths.stream().anyMatch(p -> p.contains("orders.feature")),
                    "Should find orders.feature");
            assertTrue(paths.stream().anyMatch(p -> p.contains("deep.feature")),
                    "Should find nested/deep.feature");
        }
    }

    @Test
    void testScanClasspathFromJarSubdirectory() throws Exception {
        // Create a JAR with nested structure
        Path jarPath = createTestJar("nested-features.jar",
                "com/example/features/api/users.feature",
                "com/example/features/api/orders.feature",
                "com/example/features/ui/login.feature"
        );

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            // Scan only the api subdirectory
            List<Resource> apiResults = Resource.scanClasspath("com/example/features/api", "feature", classLoader);
            assertEquals(2, apiResults.size(), "Should find 2 features in api directory");

            // Scan the ui subdirectory
            List<Resource> uiResults = Resource.scanClasspath("com/example/features/ui", "feature", classLoader);
            assertEquals(1, uiResults.size(), "Should find 1 feature in ui directory");

            // Scan all features
            List<Resource> allResults = Resource.scanClasspath("com/example/features", "feature", classLoader);
            assertEquals(3, allResults.size(), "Should find all 3 features");
        }
    }

    @Test
    void testScanClasspathFromJarReadContent() throws Exception {
        // Create a JAR with a feature file
        Path jarPath = createTestJar("content-test.jar", "test/sample.feature");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            List<Resource> results = Resource.scanClasspath("test", "feature", classLoader);

            assertEquals(1, results.size());
            Resource resource = results.get(0);

            // Verify we can read the content
            String content = resource.getText();
            assertNotNull(content);
            assertTrue(content.contains("Feature: sample"));
            assertTrue(content.contains("Scenario: Test scenario"));
        }
    }

    @Test
    void testScanClasspathFromJarEmptyDirectory() throws Exception {
        // Create a JAR with features in a different directory
        Path jarPath = createTestJar("other-dir.jar", "other/test.feature");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            // Scan a directory that doesn't exist in the JAR
            List<Resource> results = Resource.scanClasspath("features", "feature", classLoader);

            assertNotNull(results);
            assertTrue(results.isEmpty(), "Should return empty list for non-existent directory");
        }
    }

    @Test
    void testScanClasspathFromJarDifferentExtensions() throws Exception {
        // Create a JAR with mixed file types
        Path jarPath = tempDir.resolve("mixed-files.jar");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            // Add directory
            jos.putNextEntry(new JarEntry("resources/"));
            jos.closeEntry();

            // Add feature file
            jos.putNextEntry(new JarEntry("resources/test.feature"));
            jos.write("Feature: Test".getBytes());
            jos.closeEntry();

            // Add JSON file
            jos.putNextEntry(new JarEntry("resources/data.json"));
            jos.write("{\"key\": \"value\"}".getBytes());
            jos.closeEntry();

            // Add text file
            jos.putNextEntry(new JarEntry("resources/readme.txt"));
            jos.write("README content".getBytes());
            jos.closeEntry();
        }

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            // Should only find .feature files
            List<Resource> featureResults = Resource.scanClasspath("resources", "feature", classLoader);
            assertEquals(1, featureResults.size());

            // Should only find .json files
            List<Resource> jsonResults = Resource.scanClasspath("resources", "json", classLoader);
            assertEquals(1, jsonResults.size());
            assertTrue(jsonResults.get(0).getText().contains("key"));

            // Should only find .txt files
            List<Resource> txtResults = Resource.scanClasspath("resources", "txt", classLoader);
            assertEquals(1, txtResults.size());
        }
    }

    // ========== JAR Resource resolve() Tests ==========

    @Test
    void testJarResourceResolveRelativePath() throws Exception {
        // Create a JAR with related files
        Path jarPath = tempDir.resolve("resolve-test.jar");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("features/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("features/main.feature"));
            jos.write("Feature: Main\n* call read('helper.feature')".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("features/helper.feature"));
            jos.write("Feature: Helper\n* def x = 1".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("features/data/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("features/data/users.json"));
            jos.write("[{\"name\": \"John\"}]".getBytes());
            jos.closeEntry();
        }

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            // Get the main feature
            List<Resource> results = Resource.scanClasspath("features", "feature", classLoader);
            Resource mainFeature = results.stream()
                    .filter(r -> r.getRelativePath().contains("main.feature"))
                    .findFirst()
                    .orElseThrow();

            // Resolve sibling file
            Resource helper = mainFeature.resolve("helper.feature");
            assertNotNull(helper);
            assertTrue(helper.getText().contains("Feature: Helper"));

            // Resolve file in subdirectory
            Resource userData = mainFeature.resolve("data/users.json");
            assertNotNull(userData);
            assertTrue(userData.getText().contains("John"));
        }
    }

    @Test
    void testJarResourceResolveParentPath() throws Exception {
        // Create a JAR with nested structure
        Path jarPath = tempDir.resolve("parent-resolve.jar");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("base/"));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("base/common.feature"));
            jos.write("Feature: Common".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("base/sub/"));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("base/sub/child.feature"));
            jos.write("Feature: Child".getBytes());
            jos.closeEntry();
        }

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            List<Resource> results = Resource.scanClasspath("base/sub", "feature", classLoader);
            assertEquals(1, results.size());

            Resource child = results.get(0);

            // Resolve parent path (../common.feature)
            Resource common = child.resolve("../common.feature");
            assertNotNull(common);
            assertTrue(common.getText().contains("Feature: Common"));
        }
    }

    @Test
    void testClasspathResourceFromJarSingleFile() throws Exception {
        // Create a JAR with a single feature
        Path jarPath = createTestJar("single-file.jar", "test/single.feature");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            // Load single file using Resource.path() with custom classloader
            Resource resource = Resource.path("classpath:test/single.feature", classLoader);

            assertNotNull(resource);
            assertTrue(resource.isClassPath());
            assertTrue(resource.getText().contains("Feature: single"));
        }
    }

    @Test
    void testJarResourceGetSimpleName() throws Exception {
        Path jarPath = createTestJar("simplename.jar", "features/my-test.feature");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                null
        )) {
            List<Resource> results = Resource.scanClasspath("features", "feature", classLoader);
            assertEquals(1, results.size());

            Resource resource = results.get(0);
            // For JAR resources loaded via NIO, getSimpleName should work
            // For MemoryResource fallback, it may return empty - both are acceptable
            String simpleName = resource.getSimpleName();
            assertTrue(simpleName.isEmpty() || simpleName.equals("my-test.feature"),
                    "Simple name should be empty or the filename");
        }
    }

}
