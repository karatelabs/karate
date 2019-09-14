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

import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioContext;
import com.intuit.karate.core.Step;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class StackFrame {

    private final long id;
    private final int line;
    private final int column = 0;
    private final String name;
    private final Map<String, Object> source = new HashMap();

    public StackFrame(long frameId, ScenarioContext context) {
        this.id = frameId;
        Step step = context.getExecutionUnit().getCurrentStep();
        line = step.getLine();
        Scenario scenario = context.getExecutionUnit().scenario;
        name = scenario.getDisplayMeta();
        Path path = step.getFeature().getPath();
        source.put("name", path.getFileName().toString());
        source.put("path", path.toString());
        source.put("sourceReference", 0); //if not zero, source can be requested by client via a message
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap();
        map.put("id", id);
        map.put("line", line);
        map.put("column", column);
        map.put("name", name);
        map.put("source", source);
        return map;
    }

}
