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
import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import cucumber.api.CucumberOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class RestDocsHttpClientTest {
    
    @Test
    public void testRestDocs() throws Exception {        
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        // don't over-write karate-config.js
        FileUtils.copyDirectory(srcDir, destDir, 
                f -> !f.getName().equals("karate-config.js") 
                        && !f.getName().equals("upload-image.feature"), false); // TODO support binary request content
        ConfigurableApplicationContext context = Application.run(new String[]{"--server.port=0"});
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        System.setProperty("demo.server.port", ss.getLocalPort() + "");        
        KarateStats stats = CucumberRunner.parallel(getClass(), 1);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }  
    
}
