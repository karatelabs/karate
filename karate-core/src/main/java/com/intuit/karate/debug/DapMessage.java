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

import com.intuit.karate.JsonUtils;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class DapMessage {

    public static enum Type {
        REQUEST,
        RESPONSE,
        EVENT
    }

    public final int seq;
    public final Type type;
    public final String command;
    public final String event;
    
    private Map<String, Object> arguments;

    private Integer requestSeq;
    private Boolean success;
    private String message;

    
    private Map<String, Object> body;

    public DapMessage body(String key, Object value) {
        if (body == null) {
            body = new HashMap();
        }
        body.put(key, value);
        return this;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }        
    
    public Number getThreadId() {
        return getArgument("threadId", Number.class);
    }

    public <T> T getArgument(String key, Class<T> clazz) {
        if (arguments == null) {
            return null;
        }
        return (T) arguments.get(key);
    }

    public static Type parse(String s) {
        switch (s) {
            case "request":
                return Type.REQUEST;
            case "response":
                return Type.RESPONSE;
            default:
                return Type.EVENT;
        }
    }

    public static DapMessage event(int seq, String name) {
        return new DapMessage(seq, Type.EVENT, null, name);
    }

    public static DapMessage response(int seq, DapMessage req) {
        DapMessage dm = new DapMessage(seq, Type.RESPONSE, req.command, null);
        dm.requestSeq = req.seq;
        dm.success = true;
        return dm;
    }

    private DapMessage(int seq, Type type, String command, String event) {
        this.seq = seq;
        this.type = type;
        this.command = command;
        this.event = event;
    }

    public DapMessage(Map<String, Object> map) {
        seq = (Integer) map.get("seq");
        type = parse((String) map.get("type"));
        command = (String) map.get("command");
        arguments = (Map) map.get("arguments");
        requestSeq = (Integer) map.get("request_seq");
        success = (Boolean) map.get("success");
        message = (String) map.get("message");
        event = (String) map.get("event");
        body = (Map) map.get("body");
    }

    public String toJson() {
        return JsonUtils.toJson(toMap());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap(4);
        map.put("seq", seq);
        map.put("type", type.toString().toLowerCase());
        if (command != null) {
            map.put("command", command);
        }
        if (arguments != null) {
            map.put("arguments", arguments);
        }
        if (requestSeq != null) {
            map.put("request_seq", requestSeq);
        }
        if (success != null) {
            map.put("success", success);
        }
        if (message != null) {
            map.put("message", message);
        }
        if (event != null) {
            map.put("event", event);
        }
        if (body != null) {
            map.put("body", body);
        }
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[seq: ").append(seq);
        sb.append(", type: ").append(type);
        if (command != null) {
            sb.append(", command: ").append(command);
        }
        if (arguments != null) {
            sb.append(", arguments: ").append(arguments);
        }
        if (requestSeq != null) {
            sb.append(", request_seq: ").append(requestSeq);
        }
        if (success != null) {
            sb.append(", success: ").append(success);
        }
        if (message != null) {
            sb.append(", message: ").append(message);
        }
        if (event != null) {
            sb.append(", event: ").append(event);
        }
        if (body != null) {
            sb.append(", body: ").append(body);
        }
        sb.append("]");
        return sb.toString();
    }

}
