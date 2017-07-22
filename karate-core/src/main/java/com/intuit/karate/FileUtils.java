package com.intuit.karate;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import static com.intuit.karate.Script.eval;
import com.intuit.karate.cucumber.FeatureFilePath;
import com.intuit.karate.cucumber.FeatureWrapper;
import com.intuit.karate.exception.KarateFileNotFoundException;
import com.jayway.jsonpath.DocumentContext;

/**
 *
 * @author pthomas3
 */
public class FileUtils {   
    
    private FileUtils() {
        // only static methods
    }
    
    public static final boolean isClassPath(String text) {
        return text.startsWith("classpath:");
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
    
    public static final boolean isFeatureFile(String text) {
        return text.endsWith(".feature");
    }    
    
    private static String removePrefix(String text) {
        int pos = text.indexOf(':');
        return pos == -1 ? text : text.substring(pos + 1);        
    }

    public static ScriptValue readFile(String text, ScriptContext context) {
        text = StringUtils.trim(text);
        String fileName = removePrefix(text);
        fileName = StringUtils.trim(fileName);
        if (isJsonFile(text) || isXmlFile(text) || isJavaScriptFile(text)) {
            String contents = readFileAsString(fileName, isClassPath(text), context);
            return eval(contents, context);
        } else if (isTextFile(text)) {
            String contents = readFileAsString(fileName, isClassPath(text), context);
            return new ScriptValue(contents);
        } else if (isFeatureFile(text)) {
            String contents = readFileAsString(fileName, isClassPath(text), context);
            FeatureWrapper feature = FeatureWrapper.fromString(contents, context.env); // TODO determine file dir
            return new ScriptValue(feature);
        } else if (isYamlFile(text)) {
            String contents = readFileAsString(fileName, isClassPath(text), context);
            DocumentContext doc = JsonUtils.fromYaml(contents);
            return new ScriptValue(doc);
        } else {
            InputStream is = getFileStream(fileName, isClassPath(text), context);
            return new ScriptValue(is);
        }        
    }       
    
    public static String readFileAsString(String path, boolean classpath, ScriptContext context) {
        InputStream is = getFileStream(path, classpath, context);
        if (is == null) {
            String message = String.format("file not found: %s, classpath: %s", path, classpath);
            throw new KarateFileNotFoundException(message);
        }
        try {
            return IOUtils.toString(is, "utf-8");
        } catch (Exception e) {
            String message = String.format("could not read file: %s, classpath: %s", path, classpath);
            context.logger.error(message);
            throw new RuntimeException(message, e);
        }
    } 
    
    public static InputStream getFileStream(String path, boolean classpath, ScriptContext context) {
        if (classpath) {
            return context.env.fileClassLoader.getResourceAsStream(path);
        }
        String fullPath = context.env.featureDir + File.separator + path;
        try {
            InputStream is = org.apache.commons.io.FileUtils.openInputStream(new File(fullPath));
            context.logger.debug("loaded file from: {} - {}: {}", fullPath, path, is);
            return is;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static File getDirContaining(Class clazz) {
        String resourcePath = clazz.getResource(clazz.getSimpleName() + ".class").getFile();
        return new File(resourcePath).getParentFile();
    }
    
    public static URL toFileUrl(String path) {
        path = StringUtils.trim(path);
        File file = new File(path);        
        try {
            return file.getAbsoluteFile().toURI().toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static ClassLoader createClassLoader(String ... paths) {
        List<URL> urls = new ArrayList<>(paths.length);
        for (String path : paths) {
            urls.add(toFileUrl(path));
        }
        return new URLClassLoader(urls.toArray(new URL[]{}));       
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
    };    
    
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
        String[] searchPaths = { file.getParentFile().getPath() };
        return new FeatureFilePath(file, searchPaths);
    }
}
