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
package com.intuit.karate.http;

import java.util.Map;

/**
 *
 * @author pthomas3
 */
public class Session {

    private final String id;
    private final Map<String, Object> data;
    private final long created;
    private long updated;
    private long expires;

    public Session(String id, Map<String, Object> data, long created, long updated, long expires) {
        this.id = id;
        this.data = data;
        this.created = created;
        this.updated = updated;
        this.expires = expires;
    }

    public void setUpdated(long updated) {
        this.updated = updated;
    }

    public void setExpires(long expires) {
        this.expires = expires;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public long getCreated() {
        return created;
    }

    public long getUpdated() {
        return updated;
    }

    public long getExpires() {
        return expires;
    }

    @Override
    public String toString() {
        return id;
    }

}
