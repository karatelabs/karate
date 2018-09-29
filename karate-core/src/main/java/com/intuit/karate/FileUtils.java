package com.intuit.karate;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.jayway.jsonpath.DocumentContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FileUtils {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(FileUtils.class);

    public static final Charset UTF8 = StandardCharsets.UTF_8;

    private static final String CLASSPATH = "classpath";

    public static final String CLASSPATH_COLON = CLASSPATH + ":";
    public static final String THIS_COLON = "this:";
    public static final String FILE_COLON = "file:";
    public static final String SRC_TEST_JAVA = "src/test/java";
    public static final String SRC_TEST_RESOURCES = "src/test/resources";

    private FileUtils() {
        // only static methods
    }

    public static final boolean isClassPath(String text) {
        return text.startsWith(CLASSPATH_COLON);
    }

    public static final boolean isFilePath(String text) {
        return text.startsWith(FILE_COLON);
    }
    
    public static final boolean isThisPath(String text) {
        return text.startsWith(THIS_COLON);
    }    

    public static final boolean isJsonFile(String text) {
        return text.endsWith(".json");
    }

    public static final boolean isJavaScriptFile(String text) {
        return text.endsWith(".js");
    }

    public static final boolean isYamlFile(String text) {
        return text.endsWith(".yaml") || text.endsWith(".yml");
    }

    public static final boolean isXmlFile(String text) {
        return text.endsWith(".xml");
    }

    public static final boolean isTextFile(String text) {
        return text.endsWith(".txt");
    }

    public static final boolean isGraphQlFile(String text) {
        return text.endsWith(".graphql") || text.endsWith(".gql");
    }

    public static final boolean isFeatureFile(String text) {
        return text.endsWith(".feature");
    }

    public static ScriptValue readFile(String text, ScenarioContext context) {
        StringUtils.Pair pair = parsePathAndTags(text);
        text = pair.left;
        if (isJsonFile(text) || isXmlFile(text) || isJavaScriptFile(text)) {
            String contents = readFileAsString(text, context);
            ScriptValue temp = Script.evalKarateExpression(contents, context);
            return new ScriptValue(temp.getValue(), text);
        } else if (isTextFile(text) || isGraphQlFile(text)) {
            String contents = readFileAsString(text, context);
            return new ScriptValue(contents, text);
        } else if (isFeatureFile(text)) {
            Resource fr = toResource(text, context);
            Feature feature = FeatureParser.parse(fr);
            feature.setCallTag(pair.right);
            return new ScriptValue(feature, text);
        } else if (isYamlFile(text)) {
            String contents = readFileAsString(text, context);
            DocumentContext doc = JsonUtils.fromYaml(contents);
            return new ScriptValue(doc, text);
        } else {
            InputStream is = getFileStream(text, context);
            return new ScriptValue(is, text);
        }
    }

    public static String removePrefix(String text) {
        if (text == null) {
            return null;
        }
        int pos = text.indexOf(':');
        return pos == -1 ? text : text.substring(pos + 1);
    }

    private static StringUtils.Pair parsePathAndTags(String text) {
        int pos = text.indexOf('@');
        if (pos == -1) {
            text = StringUtils.trimToEmpty(text);
            return new StringUtils.Pair(text, null);
        } else {
            String left = StringUtils.trimToEmpty(text.substring(0, pos));
            String right = StringUtils.trimToEmpty(text.substring(pos));
            return new StringUtils.Pair(left, right);
        }
    }

    public static Feature parseFeatureAndCallTag(String path) {
        StringUtils.Pair pair = parsePathAndTags(path);
        Feature feature = FeatureParser.parse(pair.left);
        feature.setCallTag(pair.right);
        return feature;
    }

    private static Resource toResource(String path, ScenarioContext context) {
        if (isClassPath(path)) {
            ClassLoader cl = context.getClass().getClassLoader();
            return new Resource(fromRelativeClassPath(path, cl), path);
        } else if (isFilePath(path)) {
            String temp = removePrefix(path);
            return new Resource(new File(temp), path);
        } else if (isThisPath(path)) {
            String temp = removePrefix(path);
            Path parentPath = context.featureContext.parentPath;
            Path childPath = parentPath.resolve(temp);
            return new Resource(childPath, path);
        } else {
            try {
                Path parentPath = context.rootFeatureContext.parentPath;
                Path childPath = parentPath.resolve(path);
                return new Resource(childPath, path);
            } catch (Exception e) {
                logger.error("feature relative path resolution failed: {}", e.getMessage());
                throw e;
            }
        }
    }

    private static String readFileAsString(String path, ScenarioContext context) {
        try {
            InputStream is = getFileStream(path, context);
            return toString(is);
        } catch (Exception e) {
            String message = String.format("could not find or read file: %s", path);
            throw new KarateFileNotFoundException(message);
        }
    }

    public static InputStream getFileStream(String path, ScenarioContext context) {
        Resource fr = toResource(path, context);
        return fr.getStream();
    }

    public static String toPackageQualifiedName(String path) {
        path = removePrefix(path);
        String packagePath = path.replace("/", "."); // assumed to be already in non-windows form
        if (packagePath.endsWith(".feature")) {
            packagePath = packagePath.substring(0, packagePath.length() - 8);
        }
        return packagePath;
    }

    public static String toString(File file) {
        try {
            return toString(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(InputStream is) {
        try {
            return toByteStream(is).toString(UTF8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toPrettyString(String raw) {
        raw = StringUtils.trimToEmpty(raw);
        try {
            if (Script.isJson(raw)) {
                return JsonUtils.toPrettyJsonString(JsonUtils.toJsonDoc(raw));
            } else if (Script.isXml(raw)) {
                return XmlUtils.toString(XmlUtils.toXmlDoc(raw), true);
            }
        } catch (Exception e) {
            logger.warn("parsing failed: {}", e.getMessage());
        }
        return raw;
    }

    public static byte[] toBytes(InputStream is) {
        return toByteStream(is).toByteArray();
    }

    private static ByteArrayOutputStream toByteStream(InputStream is) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        try {
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, UTF8);
    }

    public static byte[] toBytes(String string) {
        if (string == null) {
            return null;
        }
        return string.getBytes(UTF8);
    }

    public static void copy(File src, File dest) {
        try {
            writeToFile(dest, toString(new FileInputStream(src)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeToFile(File file, String data) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data.getBytes(UTF8));
            fos.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(UTF8));
    }

    public static String removeFileExtension(String path) {
        int pos = path.lastIndexOf('.');
        if (pos == -1) {
            return path;
        } else {
            return path.substring(0, pos);
        }
    }

    public static String replaceFileExtension(String path, String extension) {
        int pos = path.lastIndexOf('.');
        if (pos == -1) {
            return path + '.' + extension;
        } else {
            return path.substring(0, pos + 1) + extension;
        }
    }

    private static final String UNKNOWN = "(unknown)";

    public static String getKarateVersion() {
        InputStream stream = FileUtils.class.getResourceAsStream("/karate-meta.properties");
        if (stream == null) {
            return UNKNOWN;
        }
        Properties props = new Properties();
        try {
            props.load(stream);
            stream.close();
            return (String) props.get("karate.version");
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    public static void renameFileIfZeroBytes(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            logger.warn("file not found, previous write operation may have failed: {}", fileName);
        } else if (file.length() == 0) {
            logger.warn("file size is zero bytes, previous write operation may have failed: {}", fileName);
            try {
                File dest = new File(fileName + ".fail");
                file.renameTo(dest);
                logger.warn("renamed zero length file to: {}", dest.getName());
            } catch (Exception e) {
                logger.warn("failed to rename zero length file: {}", e.getMessage());
            }
        }
    }

    public static boolean isFile(Path path) {
        return "file".equals(path.toUri().getScheme());
    }

    public static String toStandardPath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace('\\', '/');
    }

    public static String toRelativeClassPath(Path path, ClassLoader cl) {
        if (!isFile(path)) {
            return CLASSPATH_COLON + toStandardPath(path.toString());
        }
        for (Path rootPath : getAllClassPaths(cl)) {
            if (path.startsWith(rootPath)) {
                Path relativePath = rootPath.relativize(path);
                return CLASSPATH_COLON + toStandardPath(relativePath.toString());
            }
        }
        return null;
    }

    public static File getDirContaining(Class clazz) {
        Path path = getPathContaining(clazz);
        return path.toFile();
    }

    public static Path getPathContaining(Class clazz) {
        String relativePath = clazz.getPackage().getName().replace('.', '/');
        ClassLoader cl = clazz.getClassLoader();
        Path path = getPathIfJar(relativePath, cl);
        if (path != null) {
            return path;
        }
        // get file path
        try {
            URL url = cl.getResource(relativePath);
            return Paths.get(url.toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File getFileRelativeTo(Class clazz, String path) {
        Path dirPath = getPathContaining(clazz);
        return new File(dirPath + File.separator + path);
    }

    public static String toRelativeClassPath(Class clazz) {
        Path dirPath = getPathContaining(clazz);
        return toRelativeClassPath(dirPath, clazz.getClassLoader());
    }

    public static Path fromRelativeClassPath(String relativePath, ClassLoader cl) {
        relativePath = removePrefix(relativePath);
        try {
            return Paths.get(cl.getResource(relativePath).toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Path fromRelativeClassPath(String relativePath, Path parentPath) {
        boolean classpath = isClassPath(relativePath);
        relativePath = removePrefix(relativePath);
        if (classpath) { // use context file-system resolution
            return parentPath.resolve(relativePath);
        } else {
            return new File(relativePath).toPath();
        }
    }

    public static List<Resource> scanForFeatureFilesOnClassPath(ClassLoader cl) {
        return scanForFeatureFiles(true, CLASSPATH_COLON, cl);
    }

    public static List<Resource> scanForFeatureFiles(List<String> paths, ClassLoader cl) {
        List<Resource> list = new ArrayList();
        for (String path : paths) {
            boolean classpath = isClassPath(path);
            list.addAll(scanForFeatureFiles(classpath, path, cl));
        }
        return list;
    }

    public static List<Path> getAllClassPaths(ClassLoader classLoader) {
        try {
            List<Path> list = new ArrayList();
            Enumeration<URL> iterator = classLoader.getResources("");
            while (iterator.hasMoreElements()) {
                URL url = iterator.nextElement();
                list.add(Paths.get(url.toURI()));
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static FileSystem getFileSystem(URI uri) {
        try {
            return FileSystems.getFileSystem(uri);
        } catch (Exception e) {
            logger.warn("creating file system for URI: {} - {}", uri, e.getMessage());
            try {
                return FileSystems.newFileSystem(uri, Collections.emptyMap());
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    private static Path getPathIfJar(String relativePath, ClassLoader cl) {
        try {
            URL url = cl.getResource(relativePath);
            if (url != null && url.toURI().getScheme().equals("jar")) {
                FileSystem fileSystem = getFileSystem(url.toURI());
                return fileSystem.getPath(relativePath);
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Resource> scanForFeatureFiles(boolean classpath, String searchPath, ClassLoader cl) {
        List<Resource> files = new ArrayList();
        if (classpath) {
            searchPath = removePrefix(searchPath);
            Path search = getPathIfJar(searchPath, cl);
            if (search != null) {
                collectFeatureFilesFromJar(search, files);
            } else {
                for (Path rootPath : getAllClassPaths(cl)) {
                    collectFeatureFiles(rootPath, searchPath, files);
                }
            }
            return files;
        } else {
            collectFeatureFiles(null, searchPath, files);
            return files;
        }
    }

    private static void collectFeatureFiles(Path rootPath, String searchPath, List<Resource> files) {
        boolean classpath = rootPath != null;
        Path search;
        if (classpath) {
            search = rootPath.resolve(searchPath);
            if (!search.toFile().exists()) {
                return;
            }
        } else {
            rootPath = new File(".").getAbsoluteFile().toPath();
            search = Paths.get(searchPath);
        }
        Stream<Path> stream;
        try {
            stream = Files.walk(search);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Iterator<Path> paths = stream.iterator(); paths.hasNext();) {
            Path path = paths.next();
            if (path.getFileName().toString().endsWith(".feature")) {
                String relativePath = rootPath.relativize(path.toAbsolutePath()).toString();
                relativePath = relativePath.replaceAll("[.]{2,}", "");
                if (relativePath.charAt(0) == '/') {
                    relativePath = relativePath.substring(1);
                }
                String prefix = classpath ? CLASSPATH_COLON : "";
                files.add(new Resource(path, prefix + toStandardPath(relativePath)));
            }
        }
    }

    private static void collectFeatureFilesFromJar(Path searchPath, List<Resource> files) {
        Stream<Path> stream;
        try {
            stream = Files.walk(searchPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Iterator<Path> paths = stream.iterator(); paths.hasNext();) {
            Path path = paths.next();
            if (path.getFileName().toString().endsWith(".feature")) {
                Resource resource = new Resource(path, CLASSPATH_COLON + toStandardPath(path.toString()));
                files.add(resource);
            }
        }
    }
    
    public static enum Platform {
        WINDOWS,
        MAC,
        UNIX,
        UNKNOWN
    }
    
    public static boolean isWindows() {
        return getPlatform() == Platform.WINDOWS;
    }
    
    public static Platform getPlatform() {
    	String os = System.getProperty("os.name", "").toLowerCase();
    	if (os.contains("win")) {
            return Platform.WINDOWS;
        } else if (os.contains("mac")) {
            return Platform.MAC;
        } else if (os.contains("nix") || os.contains("nux")) {
            return Platform.UNIX;
        } else {
            return Platform.UNKNOWN;
        }        
    }

}
