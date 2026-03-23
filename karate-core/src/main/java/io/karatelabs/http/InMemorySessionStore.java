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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of SessionStore.
 * Thread-safe using ConcurrentHashMap with lazy expiry cleanup.
 * Suitable for testing and simple single-instance deployments.
 */
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 60_000; // 1 minute

    @Override
    public Session create(int expirySeconds) {
        cleanupExpiredIfNeeded();
        long now = System.currentTimeMillis();
        long expires = now + (expirySeconds * 1000L);
        String id = UUID.randomUUID().toString();
        Session session = new Session(id, new HashMap<>(), now, now, expires);
        sessions.put(id, session);
        return session;
    }

    @Override
    public Session get(String id) {
        if (id == null) {
            return null;
        }
        cleanupExpiredIfNeeded();
        Session session = sessions.get(id);
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            sessions.remove(id);
            return null;
        }
        return session;
    }

    @Override
    public void save(Session session) {
        if (session == null || session.getId() == null) {
            return;
        }
        session.setUpdated(System.currentTimeMillis());
        sessions.put(session.getId(), session);
    }

    @Override
    public void delete(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    /**
     * Get the current number of sessions in the store.
     * Useful for testing and monitoring.
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Clear all sessions from the store.
     * Useful for testing.
     */
    public void clear() {
        sessions.clear();
    }

    private boolean isExpired(Session session) {
        return session.getExpires() > 0 && System.currentTimeMillis() > session.getExpires();
    }

    private void cleanupExpiredIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanup = now;
        Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Session> entry = iterator.next();
            if (isExpired(entry.getValue())) {
                iterator.remove();
            }
        }
    }

}
