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

import io.karatelabs.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File-based implementation of SessionStore.
 * Each session is stored as a JSON file in the configured directory.
 * Suitable for single-instance deployments where sessions must survive restarts
 * (e.g., Docker containers with volume mounts).
 */
public class FileSessionStore implements SessionStore {

    private static final Logger logger = LoggerFactory.getLogger(FileSessionStore.class);

    private final Path directory;
    private long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes

    public FileSessionStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session directory: " + directory, e);
        }
    }

    @Override
    public Session create(int expirySeconds) {
        cleanupExpiredIfNeeded();
        long now = Instant.now().getEpochSecond();
        long expires = now + expirySeconds;
        String id = UUID.randomUUID().toString();
        Session session = new Session(id, new HashMap<>(), now, now, expires);
        writeToDisk(session);
        return session;
    }

    @Override
    public Session get(String id) {
        if (id == null) {
            return null;
        }
        cleanupExpiredIfNeeded();
        Session session = readFromDisk(id);
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            delete(id);
            return null;
        }
        return session;
    }

    @Override
    public void save(Session session) {
        if (session == null || session.getId() == null) {
            return;
        }
        session.setUpdated(Instant.now().getEpochSecond());
        writeToDisk(session);
    }

    @Override
    public void delete(String id) {
        if (id == null) {
            return;
        }
        Path file = sessionFile(id);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("failed to delete session file {}: {}", file, e.getMessage());
        }
    }

    private Path sessionFile(String id) {
        return directory.resolve(id + ".json");
    }

    @SuppressWarnings("unchecked")
    private Session readFromDisk(String id) {
        Path file = sessionFile(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file);
            Map<String, Object> map = (Map<String, Object>) Json.of(json).value();
            long created = ((Number) map.getOrDefault("created", 0L)).longValue();
            long updated = ((Number) map.getOrDefault("updated", 0L)).longValue();
            long expires = ((Number) map.getOrDefault("expires", 0L)).longValue();
            Map<String, Object> data = (Map<String, Object>) map.getOrDefault("data", new HashMap<>());
            return new Session(id, data, created, updated, expires);
        } catch (Exception e) {
            logger.warn("failed to read session {}: {}", id, e.getMessage());
            return null;
        }
    }

    private void writeToDisk(Session session) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", session.getId());
        map.put("created", session.getCreated());
        map.put("updated", session.getUpdated());
        map.put("expires", session.getExpires());
        map.put("data", session.getData());
        try {
            String json = Json.of(map).toString();
            Files.writeString(sessionFile(session.getId()), json);
        } catch (IOException e) {
            logger.warn("failed to write session {}: {}", session.getId(), e.getMessage());
        }
    }

    private boolean isExpired(Session session) {
        return session.getExpires() > 0 && Instant.now().getEpochSecond() > session.getExpires();
    }

    private void cleanupExpiredIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanup = now;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                String id = file.getFileName().toString().replace(".json", "");
                Session session = readFromDisk(id);
                if (session != null && isExpired(session)) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            logger.warn("session cleanup failed: {}", e.getMessage());
        }
    }

}
