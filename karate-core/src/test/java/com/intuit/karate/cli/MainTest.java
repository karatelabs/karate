/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
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
package com.intuit.karate.cli;

import com.intuit.karate.StringUtils;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MainTest {
    
    public static final String INTELLIJ1 = "com.intellij.rt.execution.application.AppMain cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^get users and then get first by id$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";
    public static final String INTELLIJ2 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos";
    public static final String INTELLIJ3 = "cucumber.api.cli.Main --plugin org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter --monochrome --name ^create and retrieve a cat$ --glue com.intuit.karate /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/java/com/intuit/karate/junit4/demos/users.feature";

    public static final String ECLIPSE1 = "com.intuit.karate.StepDefs - cucumber.api.cli.Main /Users/pthomas3/dev/zcode/karate/karate-junit4/src/test/resources/com/intuit/karate/junit4/demos/users.feature --glue classpath: --plugin pretty --monochrome";

    @Test
    public void testExtractingFeaturePathFromCommandLine() {
        String expected = "classpath:com/intuit/karate/junit4/demos/users.feature";
        String cwd = "/Users/pthomas3/dev/zcode/karate/karate-junit4";
        StringUtils.Pair path = Main.parseCommandLine(INTELLIJ1, cwd);
        assertEquals(expected, path.left);
        assertEquals("^get users and then get first by id$", path.right);
        path = Main.parseCommandLine(ECLIPSE1, cwd);
        assertEquals(expected, path.left);
        path = Main.parseCommandLine(INTELLIJ2, cwd);
        assertEquals("classpath:com/intuit/karate/junit4/demos", path.left);
        path = Main.parseCommandLine(INTELLIJ3, cwd);
        assertEquals("classpath:com/intuit/karate/junit4/demos/users.feature", path.left);
        assertEquals("^create and retrieve a cat$", path.right);
    }

}
