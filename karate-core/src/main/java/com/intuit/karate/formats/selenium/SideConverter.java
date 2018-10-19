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
package com.intuit.karate.formats.selenium;

import com.intuit.karate.FileUtils;
import com.intuit.karate.formats.selenium.SideProject;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.io.File;

/**
 * @author vmchukky
 */

// Selenium IDE SideProject file parser and converter util functions

// https://github.com/SeleniumHQ/selenium-ide/issues/77
// couldn't find version 1 of .side format yet
// picked a sample .side file to proceed with parsing for now

// may be we should also support import from old Selenium IDE
// https://github.com/SeleniumHQ/selenium-ide/issues/95
public class SideConverter {

    public static SideProject readSideProject(String json) {
        DocumentContext doc = JsonPath.parse(json);
        return new SideProject(doc);
    }

    public static String toKarateFeature(SideProject sideProject, String configJson, File dir) {
        String featureText = sideProject.convert(dir, configJson);
        File file = new File(dir, sideProject.getIdentifierName() + ".feature");
        FileUtils.writeToFile(file, featureText);
        return featureText;
    }
}
