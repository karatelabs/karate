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
package com.intuit.karate.job;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class JobMessage {

    public static final String KARATE_METHOD = "karate-method";
    public static final String KARATE_JOB_ID = "karate-job-id";    
    public static final String KARATE_EXECUTOR_ID = "karate-executor-id";
    public static final String KARATE_CHUNK_ID = "karate-chunk-id";

    public final String method;
    public final Map<String, Object> body;

    private String jobId;
    private String executorId;
    private String chunkId;
    private byte[] bytes;

    public JobMessage(String method) {
        this(method, new HashMap());
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }        

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public void setExecutorId(String executorId) {
        this.executorId = executorId;
    }

    public String getExecutorId() {
        return executorId;
    }        

    public boolean is(String method) {
        return this.method.equals(method);
    }

    public JobMessage(String method, Map<String, Object> body) {
        this.method = method;
        this.body = body;
    }

    public <T> T get(String key, Class<T> clazz) {
        return (T) body.get(key);
    }

    public JobMessage put(String key, List<JobCommand> commands) {
        if (commands == null) {
            body.remove(key);
            return this;
        }
        List<Map<String, Object>> list = new ArrayList(commands.size());
        for (JobCommand jc : commands) {
            list.add(jc.toMap());
        }
        return JobMessage.this.put(key, list);
    }

    public List<JobCommand> getCommands(String key) {
        List<Map<String, Object>> maps = get(key, List.class);
        if (maps == null) {
            return Collections.EMPTY_LIST;
        }
        List<JobCommand> list = new ArrayList(maps.size());
        for (Map<String, Object> map : maps) {
            list.add(new JobCommand(map));
        }
        return list;
    }

    public JobMessage put(String key, Object value) {
        body.put(key, value);
        return this;
    }

    public JobMessage putBase64(String key, byte[] bytes) {
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return JobMessage.this.put(key, encoded);
    }

    public byte[] getBase64(String key) {
        String encoded = get(key, String.class);
        return Base64.getDecoder().decode(encoded);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[method: ").append(method);
        if (jobId != null) {
            sb.append(", jobId: ").append(jobId);
        }
        if (executorId != null) {
            sb.append(", executorId: ").append(executorId);
        }        
        if (chunkId != null) {
            sb.append(", chunkId: ").append(chunkId);
        }
        if (body != null && !body.isEmpty()) {
            sb.append(", body: ");
            body.forEach((k, v) -> {
                sb.append("[").append(k).append(": ");
                if ("log".equals(k)) {
                    sb.append("...");
                } else if (v instanceof String) {
                    String s = (String) v;
                    if (s.length() > 1024) {
                        sb.append("...");
                    } else {
                        sb.append(s);
                    }
                } else {
                    sb.append(v);
                }
                sb.append("]");
            });
        }
        sb.append("]");
        return sb.toString();
    }

}
