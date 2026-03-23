/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
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
package io.karatelabs.http;

import io.karatelabs.js.ObjectLike;

import java.util.HashMap;
import java.util.Map;

public class Session implements ObjectLike {

    private final String id;
    private final Map<String, Object> data;
    private final long created;
    private long updated;
    private long expires;

    public static final Session TEMPORARY = new Session(null, null, -1, -1, -1);

    public static Session inMemory() {
        return new Session("-1", new HashMap<>(), -1, -1, -1);
    }

    public Session(String id, Map<String, Object> data, long created, long updated, long expires) {
        this.id = id;
        this.data = data;
        this.created = created;
        this.updated = updated;
        this.expires = expires;
    }

    public boolean isTemporary() {
        return id == null;
    }

    public Session copy() { // TODO deep-clone ?
        return new Session(id, new HashMap<>(data), created, updated, expires);
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
    public Object getMember(String name) {
        return data.get(name);
    }

    @Override
    public void putMember(String name, Object value) {
        data.put(name, value);
    }

    @Override
    public void removeMember(String s) {
        data.remove(s);
    }

    @Override
    public Map<String, Object> toMap() {
        return data;
    }

    @Override
    public String toString() {
        return id;
    }

}
