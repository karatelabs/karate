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

import com.intuit.karate.StepDefs;
import com.intuit.karate.StringUtils;
import cucumber.api.java.en.When;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class FeatureUtils {

    private static final Logger logger = LoggerFactory.getLogger(FeatureUtils.class);

    private static final List<MethodPattern> PATTERNS = new ArrayList();

    static {
        for (Method method : StepDefs.class.getMethods()) {
            When when = method.getDeclaredAnnotation(When.class);
            if (when != null) {
                MethodPattern pattern = new MethodPattern(method, when);
                PATTERNS.add(pattern);
            }
        }     
    }

    private FeatureUtils() {
        // only static methods
    }

    public static List<MethodMatch> findMethodsMatching(String text) {
        List<MethodMatch> matches = new ArrayList(1);
        for (MethodPattern pattern : PATTERNS) {
            List<String> args = pattern.match(text);
            if (args != null) {
                matches.add(new MethodMatch(pattern.method, args));
            }
        }
        return matches;
    }

    public static String toIdString(String name) {
        return name.replaceAll("[\\s_]", "-").toLowerCase();
    }

    public static StringUtils.Pair splitByFirstLineFeed(String text) {
        String left = null;
        String right = null;
        if (text != null) {
            int pos = text.indexOf('\n');
            if (pos != -1) {
                left = text.substring(0, pos).trim();
                right = text.substring(pos).trim();                
            } else {
                left = text.trim();
            }
        }
        return StringUtils.pair(left, right);
    }

}
