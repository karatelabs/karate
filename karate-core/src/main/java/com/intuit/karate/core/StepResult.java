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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class StepResult {

    private static final Map<String, Object> DUMMY_MATCH;

    private final Step step;
    private final Result result;
    private final List<FeatureResult> callResults;
    
    private boolean hidden;
    private boolean showLog = true;
    private List<Embed> embeds;
    private String stepLog;

    // short cut to re-use when converting from json
    private Map<String, Object> json;

    public String getErrorMessage() {
        if (result == null) {
            return null;
        }
        Throwable error = result.getError();
        return error == null ? null : error.getMessage();
    }

    public void appendToStepLog(String log) {
        if (log == null) {
            return;
        }
        if (stepLog == null) {
            stepLog = "";
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
    
    private static List<Map> tableToMap(Table table) {
        List<List<String>> rows = table.getRows();
        List<Map> list = new ArrayList(rows.size());
        int count = rows.size();
        for (int i = 0; i < count; i++) {
            List<String> row = rows.get(i);
            Map<String, Object> map = new HashMap(2);
            map.put("cells", row);
            map.put("line", table.getLineNumberForRow(i));
            list.add(map);
        }
        return list;
    }

    public StepResult(Map<String, Object> map) {
        json = map;
        step = new Step();
        step.setLine((Integer) map.get("line"));
        step.setPrefix((String) map.get("prefix"));
        step.setText((String) map.get("name"));
        result = new Result((Map) map.get("result"));
        callResults = null;
    }

    public Map<String, Object> toMap() {
        if (json != null) {
            return json;
        }
        Map<String, Object> map = new HashMap(8);
        map.put("line", step.getLine());
        map.put("keyword", step.getPrefix());
        map.put("name", step.getText());
        map.put("result", result.toMap());
        map.put("match", DUMMY_MATCH);
        StringBuilder sb = new StringBuilder();
        if (step.getDocString() != null) {
            sb.append(step.getDocString());
        }
        if (stepLog != null && showLog) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(stepLog);
        }
        if (sb.length() > 0) {
            map.put("doc_string", docStringToMap(step.getLine(), sb.toString()));
        }
        if (step.getTable() != null) {
            map.put("rows", tableToMap(step.getTable()));
        }
        if (embeds != null) {
            List<Map> embedList = new ArrayList(embeds.size());
            for (Embed embed : embeds) {
                embedList.add(embed.toMap());
            }
            map.put("embeddings", embedList);
        }
        return map;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }        

    public boolean isHidden() {
        return hidden;
    }

    public boolean isShowLog() {
        return showLog;
    }

    public void setShowLog(boolean showLog) {
        this.showLog = showLog;
    }        

    public boolean isStopped() {
        return result.isFailed() || result.isAborted();
    }

    public StepResult(Step step, Result result, String stepLog, List<Embed> embeds, List<FeatureResult> callResults) {
        this.step = step;
        this.result = result;
        this.stepLog = stepLog;
        this.embeds = embeds;
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

    public List<Embed> getEmbeds() {
        return embeds;
    }

    public void addEmbed(Embed embed) {
        if (embeds == null) {
            embeds = new ArrayList();
        }
        embeds.add(embed);
    }

    public List<FeatureResult> getCallResults() {
        return callResults;
    }

}
