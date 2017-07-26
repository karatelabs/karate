/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package com.intuit.karate.convert;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * Created by rkumar32 on 7/5/17.
 */
public class KarateFeatureWriter {
    private static final Logger logger = LoggerFactory.getLogger(PostmanCollectionReader.class);

    private KarateFeatureWriter() {
        // only static methods
    }

    public static File write(List<PostmanRequest> requests, String path) {
        String feature = getFeature(requests);
        String inputFileName = new File(path).getName();
        String outputFileName = inputFileName.replace("postman_collection", "feature");
        String dirPath = new File(path).getParentFile().getPath();
        File featureFile;
        try {
            featureFile = new File(dirPath + "/" + outputFileName);
            FileUtils.writeStringToFile(featureFile, feature, "utf-8");
            return featureFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFeature(List<PostmanRequest> requests) {
        String scenarios = "";
        for (PostmanRequest request : requests) {
            scenarios += request.convert();
        }
        String feature = "Feature: \n\n" + scenarios;
        return feature;
    }
}
