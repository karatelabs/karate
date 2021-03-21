/*
 * The MIT License
 *
 * Copyright 2019 Intuit Inc.
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
package com.intuit.karate.debug;

import com.intuit.karate.Json;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.core.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class SourceBreakpoints {

    public final String name;
    public final String path;
    public final List<Breakpoint> breakpoints;
    public final boolean sourceModified;
    
    public boolean isBreakpoint(int line, ScenarioRuntime context) {
        if (breakpoints == null || breakpoints.isEmpty()) {
            return false;
        }
        for (Breakpoint b : breakpoints) {
            if (b.line == line) {
                if (b.condition == null) {
                    return true;
                } else {
                    Variable evalCondition = context.engine.evalKarateExpression(b.condition);
                    if (evalCondition != null && evalCondition.type != Variable.Type.BOOLEAN) {
                        // if the condition is not a boolean then what are you doing trying to use it as a condition?
                        return true;
                    }

                    return evalCondition != null && evalCondition.isTrue();
                }
            }
        }
        return false;
    }

    public SourceBreakpoints(Map<String, Object> map) {
        Json json = Json.of(map);
        name = json.get("source.name");
        path = json.get("source.path");
        List<Map<String, Object>> list = json.get("breakpoints");
        breakpoints = new ArrayList(list.size());
        for (Map<String, Object> bm : list) {
            breakpoints.add(new Breakpoint(bm));
        }
        sourceModified = json.get("sourceModified");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[name: ").append(name);
        sb.append(", path: ").append(path);
        sb.append(", breakpoints: ").append(breakpoints);
        sb.append(", sourceModified: ").append(sourceModified);
        sb.append("]");
        return sb.toString();
    }

}
