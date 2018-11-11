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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class StepResult  {

    private static final Map<String, Object> DUMMY_MATCH;
    
    private final Step step;
    private final Result result;    
    private final Embed embed;
    private final List<FeatureResult> callResults;
    private final boolean hidden;
    
    private String stepLog;
    
    public void appendToStepLog(String log) {
        if (log == null || stepLog == null) {
            return;
        }
        stepLog = stepLog + log;
    }

    static {
        DUMMY_MATCH = new HashMap(2);
        DUMMY_MATCH.put("location", "karate");
        DUMMY_MATCH.put("arguments", Collections.EMPTY_LIST);
    }

    private static Map<String, Object> docStringToMap(int line, String text) {
        Map<String, Object> map = new HashMap(3);
        map.put("content_type", "");
        map.put("line", line);
        map.put("value", text);
        return map;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(6);
        map.put("line", step.getLine());
        map.put("keyword", step.getPrefix());
        map.put("name", step.getText());
        map.put("result", result.toMap());
        map.put("match", DUMMY_MATCH);
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            sb.append(step.getDocString());
        }
        if (stepLog != null) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(stepLog);
        }
        if (sb.length() > 0) {
            map.put("doc_string", docStringToMap(step.getLine(), sb.toString()));
        }
        if (embed != null) {
            Map embedMap = new HashMap(2);
            embedMap.put("data", embed.getBase64());
            embedMap.put("mime_type", embed.getMimeType());
            map.put("embeddings", Collections.singletonList(embedMap));
        }
        return map;
    }

    public boolean isHidden() {
        return hidden;
    }        
        
    public boolean isStopped() {
        return result.isFailed() || result.isAborted();
    }    

    public StepResult(boolean hidden, Step step, Result result, String stepLog, Embed embed, List<FeatureResult> callResults) {
        this.hidden = hidden;
        this.step = step;
        this.result = result;
        this.stepLog = stepLog;
        this.embed = embed;
        this.callResults = callResults;
    }   

    public Step getStep() {
        return step;
    }        
    
    public Result getResult() {
        return result;
    }

    public String getStepLog() {
        return stepLog;
    }

    public Embed getEmbed() {
        return embed;
    }        

    public List<FeatureResult> getCallResults() {
        return callResults;
    }        
    
}
