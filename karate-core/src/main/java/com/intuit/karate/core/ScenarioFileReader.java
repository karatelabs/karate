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
        StringUtils.Pair pair = parsePathAndTags(text);
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

    private static StringUtils.Pair parsePathAndTags(String text) {
        int pos = text.indexOf(".feature@");
        if (pos == -1) {
            text = StringUtils.trimToEmpty(text);
            return new StringUtils.Pair(text, null);
        } else {
            return new StringUtils.Pair(StringUtils.trimToEmpty(text.substring(0, pos + 8)), StringUtils.trimToEmpty(text.substring(pos + 9)));
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
