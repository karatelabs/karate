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
    
    public boolean isBreakpoint(int line) {
        if (breakpoints == null || breakpoints.isEmpty()) {
            return false;
        }
        for (Breakpoint b : breakpoints) {
            if (b.line == line) {
                return true;
            }
        }
        return false;
    }

    public SourceBreakpoints(Map<String, Object> map) {
        Json json = new Json(map);
        name = json.getString("source.name");
        path = json.getString("source.path");
        List<Map<String, Object>> list = json.getList("breakpoints");
        breakpoints = new ArrayList(list.size());
        for (Map<String, Object> bm : list) {
            breakpoints.add(new Breakpoint(bm));
        }
        sourceModified = json.getBoolean("sourceModified");
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
