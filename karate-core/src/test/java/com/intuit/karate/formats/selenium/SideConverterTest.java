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

import com.intuit.karate.formats.selenium.SideConverter;
import com.intuit.karate.FileUtils;
import com.intuit.karate.formats.selenium.SideProject;
import com.intuit.karate.formats.selenium.TestSuite;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * @author vmchukky
 */
public class SideConverterTest {

    @Test
    public void readSideProject() {
        File file = FileUtils.getFileRelativeTo(getClass(), "side-converter-test.side");
        String sideJson = FileUtils.toString(file);
        SideProject project = SideConverter.readSideProject(sideJson);
        Assert.assertEquals("https://github.com", project.getUrl());
        List<TestSuite> suites = project.getSuites();
        Assert.assertTrue(suites.size() == 3);
        Assert.assertTrue(suites.get(0).getTests().size() == 2);
        Assert.assertTrue(suites.get(1).getTests().size() == 1);
        Assert.assertTrue(suites.get(1).getTests().size() == 1);

        File dir = new File("target");
        String configJson = "{'browser':'chrome','url':'http://localhost:9515'}";
        String featureText = SideConverter.toKarateFeature(project, configJson, dir);
        String contents = FileUtils.toString(new File(dir, project.getIdentifierName() + ".feature"));
        Assert.assertEquals(contents, featureText);

        contents = FileUtils.toString(new File(dir, suites.get(0).getIdentifierName() + ".feature"));
        Assert.assertTrue(contents.startsWith("@ignore\nFeature: Suite-1-d30a2cb2-69ca-4f43-8e9a-17853c84ede0"));

        contents = FileUtils.toString(new File(dir, suites.get(1).getIdentifierName() + ".feature"));
        Assert.assertTrue(contents.startsWith("@ignore\nFeature: Suite-2-6420ca81-e4c4-400e-a67f-8d592368d206"));

        contents = FileUtils.toString(new File(dir, suites.get(2).getIdentifierName() + ".feature"));
        Assert.assertTrue(contents.startsWith("@ignore\nFeature: no-name-default-test-suite"));

    }
}