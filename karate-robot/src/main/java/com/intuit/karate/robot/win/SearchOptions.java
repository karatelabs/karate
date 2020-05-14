/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.robot.win;

import com.intuit.karate.StringUtils;
import java.util.function.Predicate;

/**
 *
 * @author pthomas3
 */
public class SearchOptions {

    public final String original;
    public final String controlType;
    public final String className;
    public final Predicate<String> nameCondition;
    public final String name;
    public final int index;

    private int matchCount;

    public boolean matches(IUIAutomationElement e) {
        if (controlType != null) {
            if (!e.getControlType().name().equalsIgnoreCase(controlType)) {
                return false;
            }
        }
        if (name != null) {
            if (!nameCondition.test(e.getCurrentName())) {
                return false;
            }
        }
        if (className != null) {
            if (!e.getClassName().equalsIgnoreCase(className)) {
                return false;
            }
        }
        if (matchCount++ != index) {
            return false;
        }
        return true;
    }

    public SearchOptions(String locator) {
        this.original = locator;
        // this is assumed to start with "{"
        int pos = locator.indexOf('}');
        if (pos == -1) {
            throw new RuntimeException("bad locator prefix: " + locator);
        }
        name = StringUtils.trimToNull(locator.substring(pos + 1));
        String prefix;
        if (locator.charAt(1) == '^') {
            nameCondition = s -> s.contains(name);
            prefix = locator.substring(2, pos);
        } else {
            nameCondition = s -> s.equals(name);
            prefix = locator.substring(1, pos);
        }
        pos = prefix.indexOf(':');
        if (pos != -1) {
            StringUtils.Pair pair = parseTypeAndClass(prefix.substring(0, pos));
            controlType = pair.left;
            className = pair.right;
            String indexTemp = prefix.substring(pos + 1);
            if (indexTemp.isEmpty()) {
                index = 0;
            } else {
                try {
                    index = Integer.valueOf(indexTemp) - 1; // 1 based index for end user 
                } catch (Exception e) {
                    throw new RuntimeException("bad locator prefix: " + locator + ", " + e.getMessage());
                }
            }
        } else {
            StringUtils.Pair pair = parseTypeAndClass(prefix);
            controlType = pair.left;
            className = pair.right;
            index = 0;
        }
    }

    private static StringUtils.Pair parseTypeAndClass(String type) {
        if (type == null || type.isEmpty()) {
            return StringUtils.pair(null, null);
        }
        int dotPos = type.indexOf('.');
        if (dotPos == -1) {
            return StringUtils.pair(type, null);
        } else {
            String left = StringUtils.trimToNull(type.substring(0, dotPos));
            String right = StringUtils.trimToNull(type.substring(dotPos + 1));
            return StringUtils.pair(left, right);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(original);
        sb.append(", controlType: ").append(controlType);
        sb.append(", name: ").append(name);
        sb.append(", index: ").append(index);
        sb.append("]");
        return sb.toString();
    }

}
