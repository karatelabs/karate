/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.StringUtils;
import com.intuit.karate.JsonUtils;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author pthomas3
 */
public class ScenarioFileReader {

    private final ScenarioEngine engine;
    private final FeatureRuntime featureRuntime;

    public ScenarioFileReader(ScenarioEngine engine, FeatureRuntime featureRuntime) {
        this.engine = engine;
        this.featureRuntime = featureRuntime;
    }

    public Object readFile(String text) {
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        StringUtils.Pair pair = parsePathAndTags(text, left, right);
        text = pair.left;
        if (isJsonFile(text) || isXmlFile(text)) {
            String contents = readFileAsString(text);
            Variable temp = engine.evalKarateExpression(contents);
            return temp.getValue();
        } else if (isJavaScriptFile(text)) {
            String contents = readFileAsString(text);
            Variable temp = engine.evalJs("(" + contents + ")");
            return temp.getValue();
        } else if (isTextFile(text) || isGraphQlFile(text)) {
            return readFileAsString(text);
        } else if (isFeatureFile(text)) {
            Resource fr = toResource(text);
            Feature feature = Feature.read(fr);
            feature.setCallTag(pair.right);
            return feature;
        } else if (isCsvFile(text)) {
            String contents = readFileAsString(text);
            return JsonUtils.fromCsv(contents);
        } else if (isYamlFile(text)) {
            String contents = readFileAsString(text);
            Object asJson = JsonUtils.fromYaml(contents);
            Variable temp = engine.evalKarateExpression(JsonUtils.toJson(asJson));
            return temp.getValue();
        } else {
            InputStream is = readFileAsStream(text);
            return FileUtils.toBytes(is); // TODO stream
        }
    }

    public File relativePathToFile(String relativePath) {
        return toResource(relativePath).getFile();
    }

    public String toAbsolutePath(String relativePath) {
        Resource resource = toResource(relativePath);
        try {
            return resource.getFile().getCanonicalPath();
        } catch (IOException e) {
            return resource.getFile().getAbsolutePath();
        }
    }

    public byte[] readFileAsBytes(String path) {
        return FileUtils.toBytes(readFileAsStream(path));
    }

    public String readFileAsString(String path) {
        return FileUtils.toString(readFileAsStream(path));
    }

    public InputStream readFileAsStream(String path) {
        return toResource(path).getStream();
    }

    private static String removePrefix(String text) {
        if (text == null) {
            return null;
        }
        int pos = text.indexOf(':');
        return pos == -1 ? text : text.substring(pos + 1);
    }

    /**
     * This method looks at the file read () in feature file and determines if the @ is contained in the file path then
     * is the text on left side contains "." to represent file extension. If yes then left and right part are
     * extracted and returned. However, if the left side does not contain "." then it means that file or
     * directory name contains @ symbol and the code recursevly sets left path upto @ and text input = right part.
     * Ex - //read(call-by-tag-called.feature@name=second) --- here before @, . is contained so no recursive call will made.
     *     //C:\Dinesh\karate-test\@example@s\call-by-tag-called.feature@name=second -- here in the first recursion
     *     left = C:\Dinesh\karate-test\@, right = "" and text = example@s\call-by-tag-called.feature@name=second
     *     Second recursion, left = C:\Dinesh\karate-test\@example@, right = "" and text = s\call-by-tag-called.feature@name=second
     *     final result left = C:\Dinesh\karate-test\@example@s\call-by-tag-called.feature and right = name=second
     * @param text
     * @param left
     * @param right
     * @return
     */
    private static StringUtils.Pair parsePathAndTags(String text,
                                                     StringBuilder left,
                                                     StringBuilder right) {
        int pos = text.indexOf('@');
        if (pos == -1) {
            text = StringUtils.trimToEmpty(text);
            return new StringUtils.Pair(left.append(text).toString(), null);
        } else {
            if(StringUtils.trimToEmpty(text.substring(0, pos)).contains(".")){
                left = left.append(StringUtils.trimToEmpty(text.substring(0, pos)));
                right =right.append(StringUtils.trimToEmpty(text.substring(pos)));
            }else{
                left = left.append(StringUtils.trimToEmpty(text.substring(0, pos+1)));
                parsePathAndTags(StringUtils.trimToEmpty(text.substring(pos+1)), left, right);

            }

            return new StringUtils.Pair(left.toString(), right.toString());
        }
    }


    public Resource toResource(String path) {
        if (isClassPath(path)) {
            return ResourceUtils.getResource(featureRuntime.suite.workingDir, path);
        } else if (isFilePath(path)) {
            return ResourceUtils.getResource(featureRuntime.suite.workingDir, removePrefix(path));
        } else if (isThisPath(path)) {
            return featureRuntime.resolveFromThis(removePrefix(path));
        } else {
            return featureRuntime.resolveFromRoot(path);
        }
    }

    private static boolean isClassPath(String text) {
        return text.startsWith("classpath:");
    }

    private static boolean isFilePath(String text) {
        return text.startsWith("file:");
    }

    private static boolean isThisPath(String text) {
        return text.startsWith("this:");
    }

    private static boolean isJsonFile(String text) {
        return text.endsWith(".json");
    }

    private static boolean isJavaScriptFile(String text) {
        return text.endsWith(".js");
    }

    private static boolean isYamlFile(String text) {
        return text.endsWith(".yaml") || text.endsWith(".yml");
    }

    private static boolean isXmlFile(String text) {
        return text.endsWith(".xml");
    }

    private static boolean isTextFile(String text) {
        return text.endsWith(".txt");
    }

    private static boolean isCsvFile(String text) {
        return text.endsWith(".csv");
    }

    private static boolean isGraphQlFile(String text) {
        return text.endsWith(".graphql") || text.endsWith(".gql");
    }

    private static boolean isFeatureFile(String text) {
        return text.endsWith(".feature");
    }

}
