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
package demo;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.KarateOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = {"~@ignore", "~@mock-servlet-todo"})
public class MockSpringMvcServletTest {
    
    @Test
    public void testSpringBootDemo() throws Exception {        
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        FileUtils.copyDirectory(srcDir, destDir, 
                f -> !f.getName().equals("karate-config.js"), false); // don't over-write karate-config.js
        System.setProperty("karate.env", "dev-mock-springmvc");
        Results results = Runner.parallel(getClass(), 5);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }
    
}
