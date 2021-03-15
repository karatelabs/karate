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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author pthomas3
 */
public class JvmSessionStore implements SessionStore {

    public static final SessionStore INSTANCE = new JvmSessionStore();

    private static final AtomicLong COUNTER = new AtomicLong();

    private final Map<String, Session> sessions = new ConcurrentHashMap();

    private JvmSessionStore() {
        // singleton
    }

    @Override
    public Session create(long now, long expires) {
        String id = COUNTER.incrementAndGet() + "-" + System.currentTimeMillis();
        return new Session(id, new HashMap(), now, now, expires);
    }

    @Override
    public Session get(String id) {
        return sessions.get(id);
    }

    @Override
    public void save(Session session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public void delete(String id) {
        sessions.remove(id);
    }        

}
