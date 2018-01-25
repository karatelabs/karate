package com.intuit.karate;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import com.intuit.karate.cucumber.FeatureFilePath;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.jayway.jsonpath.DocumentContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import static com.intuit.karate.Script.evalKarateExpression;

/**
 *
 * @author pthomas3
 */
public class FileUtils {

    public static final Charset UTF8 = StandardCharsets.UTF_8;

    private static final String CLASSPATH = "classpath";
    public static final String CLASSPATH_COLON = CLASSPATH + ":";

    private FileUtils() {
        // only static methods
    }

    public static final boolean isClassPath(String text) {
        return text.startsWith(CLASSPATH_COLON);
    }

    public static final boolean isFilePath(String text) {
        return text.startsWith("file:");
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

    private static String removePrefix(String text) {
        int pos = text.indexOf(':');
        return pos == -1 ? text : text.substring(pos + 1);
    }

    private static enum PathPrefix {
        NONE,
        CLASSPATH,
        FILE
    }

    public static ScriptValue readFile(String text, ScriptContext context) {
        text = StringUtils.trimToEmpty(text);
        PathPrefix prefix = isClassPath(text) ? PathPrefix.CLASSPATH : (isFilePath(text) ? PathPrefix.FILE : PathPrefix.NONE);
        String fileName = removePrefix(text);
        fileName = StringUtils.trimToEmpty(fileName);
        if (isJsonFile(text) || isXmlFile(text) || isJavaScriptFile(text)) {
            String contents = readFileAsString(fileName, prefix, context);
            ScriptValue temp = evalKarateExpression(contents, context);
            return new ScriptValue(temp.getValue(), text);
        } else if (isTextFile(text) || isGraphQlFile(text)) {
            String contents = readFileAsString(fileName, prefix, context);
            return new ScriptValue(contents, text);
        } else if (isFeatureFile(text)) {
            String contents = readFileAsString(fileName, prefix, context);
            FeatureWrapper feature = FeatureWrapper.fromString(contents, context.env, text);
            return new ScriptValue(feature, text);
        } else if (isYamlFile(text)) {
            String contents = readFileAsString(fileName, prefix, context);
            DocumentContext doc = JsonUtils.fromYaml(contents);
            return new ScriptValue(doc, text);
        } else {
            InputStream is = getFileStream(fileName, prefix, context);
            return new ScriptValue(is, text);
        }
    }

    private static String readFileAsString(String path, PathPrefix prefix, ScriptContext context) {
        try {
            InputStream is = getFileStream(path, prefix, context);
            return toString(is);
        } catch (Exception e) {
            String message = String.format("could not read file: %s, prefix: %s", path, prefix);
            context.logger.error(message);
            throw new KarateFileNotFoundException(message);
        }
    }

    public static InputStream getFileStream(String text, ScriptContext context) {
        text = StringUtils.trimToEmpty(text);
        PathPrefix prefix = isClassPath(text) ? PathPrefix.CLASSPATH : (isFilePath(text) ? PathPrefix.FILE : PathPrefix.NONE);
        String fileName = removePrefix(text);
        fileName = StringUtils.trimToEmpty(fileName);
        return getFileStream(fileName, prefix, context);
    }

    private static InputStream getFileStream(String path, PathPrefix prefix, ScriptContext context) {
        switch (prefix) {
            case CLASSPATH:
                return context.env.fileClassLoader.getResourceAsStream(path);
            case NONE: // relative to feature dir
                path = context.env.featureDir + File.separator + path;
                break;
            default: // as-is
        }
        try {
            return new FileInputStream(path);
        } catch (FileNotFoundException e) {
            throw new KarateFileNotFoundException(e.getMessage());
        }
    }

    public static File resolveIfClassPath(String path) {
        File file = new File(path);
        if (file.exists()) { // loaded by karate
            return file;
        } else { // was loaded by cucumber-jvm, is relative to classpath
            String temp = file.getPath().replace('\\', '/'); // fix for windows
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            String actualPath = cl.getResource(temp).getFile();
            return new File(actualPath);
        }
    }

    public static File getDirContaining(Class clazz) {
        String resourcePath = clazz.getResource(clazz.getSimpleName() + ".class").getFile();
        return new File(resourcePath).getParentFile();
    }

    public static File getFileRelativeTo(Class clazz, String path) {
        File dir = FileUtils.getDirContaining(clazz);
        return new File(dir.getPath() + File.separator + path);
    }

    public static URL toFileUrl(String path) {
        path = StringUtils.trimToEmpty(path);
        File file = new File(path);
        try {
            return file.getAbsoluteFile().toURI().toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ClassLoader createClassLoader(String... paths) {
        List<URL> urls = new ArrayList<>(paths.length);
        for (String path : paths) {
            urls.add(toFileUrl(path));
        }
        return new URLClassLoader(urls.toArray(new URL[]{}));
    }

    public static String toPackageQualifiedName(String path) {
        String packagePath = path.replace("/", "."); // assumed to be already in non-windows form
        if (packagePath.endsWith(".feature")) {
            packagePath = packagePath.substring(0, packagePath.length() - 8);
        }
        return packagePath;
    }

    public static String getFeaturePath(String commandLine, String cwd) {
        cwd = cwd.replace('\\', '/'); // fix for windows
        int start = commandLine.indexOf(cwd);
        if (start == -1) {
            return null;
        }
        int end = commandLine.indexOf(".feature", start);
        if (end == -1) {
            return null;
        }
        return commandLine.substring(start, end + 8);
    }

    private static String searchPattern(String one, String two, char c) {
        return c + "src" + c + one + c + two + c;
    }

    private static final String[] SEARCH_PATTERNS = {
        searchPattern("test", "java", '/'),
        searchPattern("test", "resources", '/'),
        searchPattern("main", "java", '/'),
        searchPattern("main", "resources", '/')
    };

    private static String[] getSearchPaths(String rootPath) {
        String[] res = new String[SEARCH_PATTERNS.length];
        for (int i = 0; i < SEARCH_PATTERNS.length; i++) {
            res[i] = new File(rootPath + SEARCH_PATTERNS[i]).getPath();
        }
        return res;
    }

    ;    
    
    public static FeatureFilePath parseFeaturePath(File file) {
        String path = file.getAbsolutePath();
        path = path.replace('\\', '/'); // normalize windows
        for (String pattern : SEARCH_PATTERNS) {
            int pos = path.lastIndexOf(pattern);
            if (pos != -1) { // found
                String rootPath = path.substring(0, pos);
                String[] searchPaths = getSearchPaths(rootPath);
                return new FeatureFilePath(file, searchPaths);
            }
        }
        String[] searchPaths = {file.getParentFile().getPath()};
        return new FeatureFilePath(file, searchPaths);
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
            file.getParentFile().mkdirs();
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

    public static List<String> toStringLines(String text) {
        return new BufferedReader(new StringReader(text)).lines().collect(Collectors.toList());
    }

    public static String replaceFileExtension(String path, String extension) {
        int pos = path.lastIndexOf('.');
        if (pos == -1) {
            return path + '.' + extension;
        } else {
            return path.substring(0, pos + 1) + extension;
        }
    }

}
