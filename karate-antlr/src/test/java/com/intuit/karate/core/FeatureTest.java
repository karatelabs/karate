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
package com.intuit.karate.core;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import java.io.File;
import java.io.FileInputStream;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureTest {
    
    private static final Logger logger = LoggerFactory.getLogger(FeatureTest.class);
    
    private static int count;
    
    @Test
    public void testParsingAllFeaturesInKarate() {
        recurse(new File(".."));
    }
    
    @Test
    public void testSimple() throws Exception {
        File file = FileUtils.getFileRelativeTo(getClass(), "test-simple.feature");
        ScriptEnv env = ScriptEnv.init(file.getParentFile(), file.getName(), Thread.currentThread().getContextClassLoader());        
        StepDefs stepDefs = new StepDefs(env, new CallContext(null, true));        
        Feature feature = new Feature(new FileInputStream(file));
        feature.execute(stepDefs);
    }
    
    private static void recurse(File dir) {        
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                recurse(file);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".feature")) {
                    count++;
                    logger.debug("parsing: {} {}", count, file.getPath());
                    try {
                        parse(file.getPath());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public static Feature parse(String fileName) throws Exception {
        FileInputStream fis = new FileInputStream(fileName);
        return new Feature(fis);        
    }    
    
}
