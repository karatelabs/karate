/*
 * The MIT License
 *
 * Copyright 2022 Karate Labs Inc.
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
package com.intuit.karate;

import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.graal.JsValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author pthomas3
 */
public class MatchOperation {

    public static final String REGEX = "regex";        

    final Match.Context context;
    final MatchOperator type;
    final Match.Value actual;
    final Match.Value expected;
    final List<MatchOperation> failures;

    boolean pass = true;
    String failReason;

    MatchOperation(MatchOperator type, Match.Value actual, Match.Value expected) {
        this(JsEngine.global(), null, type, actual, expected);
    }

    MatchOperation(JsEngine js, MatchOperator type, Match.Value actual, Match.Value expected) {
        this(js, null, type, actual, expected);
    }

    MatchOperation(Match.Context context, MatchOperator type, Match.Value actual, Match.Value expected) {
        this(null, context, type, actual, expected);
    }

    private MatchOperation(JsEngine js, Match.Context context, MatchOperator type, Match.Value actual, Match.Value expected) {
        this.type = type;
        this.actual = actual;
        this.expected = expected;
        if (context == null) {
            if (js == null) {
                js = JsEngine.global();
            }
            this.failures = new ArrayList();
            if (actual.isXml()) {
                this.context = new Match.Context(js, this, true, 0, "/", "", -1);
            } else {
                this.context = new Match.Context(js, this, false, 0, "$", "", -1);
            }
        } else {
            this.context = context;
            this.failures = context.root.failures;
        }        
    }

    boolean execute() {
        return type.execute(this);
    }

    boolean pass() {
        pass = true;
        return true;
    }

    boolean fail(String reason) {
        pass = false;
        if (reason == null) {
            return false;
        }
        failReason = failReason == null ? reason : reason + " | " + failReason;
        context.root.failures.add(this);
        return false;
    }

    String getFailureReasons() {
        return collectFailureReasons(this);
    }

    private boolean isXmlAttributeOrMap() {
        return context.xml && actual.isMap()
                && (context.name.equals("@") || actual.<Map>getValue().containsKey("_"));
    }

    private static String collectFailureReasons(MatchOperation root) {
        StringBuilder sb = new StringBuilder();
        sb.append("match failed: ").append(root.type).append('\n');
        Collections.reverse(root.failures);
        Iterator<MatchOperation> iterator = root.failures.iterator();
        Set previousPaths = new HashSet();
        int index = 0;
        int prevDepth = -1;
        while (iterator.hasNext()) {
            MatchOperation mo = iterator.next();
            if (previousPaths.contains(mo.context.path) || mo.isXmlAttributeOrMap()) {
                continue;
            }
            previousPaths.add(mo.context.path);
            if (mo.context.depth != prevDepth) {
                prevDepth = mo.context.depth;
                index++;
            }
            String prefix = StringUtils.repeat(' ', index * 2);
            sb.append(prefix).append(mo.context.path).append(" | ").append(mo.failReason);
            sb.append(" (").append(mo.actual.type).append(':').append(mo.expected.type).append(")");
            sb.append('\n');
            if (mo.context.xml) {
                sb.append(prefix).append(mo.actual.getAsXmlString()).append('\n');
                sb.append(prefix).append(mo.expected.getAsXmlString()).append('\n');
            } else {
                Match.Value expected = mo.expected.getSortedLike(mo.actual);
                sb.append(prefix).append(mo.actual.getWithinSingleQuotesIfString()).append('\n');
                sb.append(prefix).append(expected.getWithinSingleQuotesIfString()).append('\n');
            }
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

}
