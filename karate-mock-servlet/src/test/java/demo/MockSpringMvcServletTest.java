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

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import cucumber.api.CucumberOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class MockSpringMvcServletTest {
    
    @Test
    public void testSpringBootDemo() throws Exception {        
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        FileUtils.copyDirectory(srcDir, destDir, 
                f -> !f.getName().equals("karate-config.js") // don't over-write karate-config.js
                        && !f.getName().equals("redirect.feature") // too much work to support redirects in mock servlet
                        && !f.getName().equals("request.feature") // TODO support (karate.request) in mock servlet
                        && !f.getName().equals("content-type.feature") // TODO empty content type
                        && !f.getName().equals("sign-in.feature"), false); // TODO support servlet filters
        System.setProperty("karate.env", "dev-mock-springmvc");
        KarateStats stats = CucumberRunner.parallel(getClass(), 5);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }
    
}
