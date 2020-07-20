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

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import java.util.Collections;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class ScenarioHookTest {
    
    @Test
    public void testStopIfScenarioHasNoTags() {
        String path = "classpath:com/intuit/karate/core/test-hook-notags.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeatureCount());
        assertEquals(1, results.getFailCount());
    }
    
    @Test
    public void testHookForExamplesWithTags() {
        String path = "classpath:com/intuit/karate/core/test-hook-multiexample.feature";
        Results results = Runner.path(path).hook(new MandatoryTagHook()).parallel(1);
        assertEquals(1, results.getFeatureCount());
        assertEquals(7, results.getScenarioCount());
        assertEquals(0, results.getFailCount());
    }    
    
}
