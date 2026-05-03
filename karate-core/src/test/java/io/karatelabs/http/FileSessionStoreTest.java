package io.karatelabs.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSessionStoreTest {

    @TempDir
    Path tempDir;

    FileSessionStore store;

    @BeforeEach
    void setUp() {
        store = new FileSessionStore(tempDir.resolve("sessions"));
    }

    @Test
    void testCreateAndGet() {
        Session session = store.create(600);
        assertNotNull(session.getId());
        assertNotNull(session.getData());
        assertTrue(Files.exists(tempDir.resolve("sessions").resolve(session.getId() + ".json")));

        Session loaded = store.get(session.getId());
        assertNotNull(loaded);
        assertEquals(session.getId(), loaded.getId());
    }

    @Test
    void testSaveAndReload() {
        Session session = store.create(600);
        session.putMember("user", "alice");
        session.putMember("count", 42);
        store.save(session);

        Session loaded = store.get(session.getId());
        assertNotNull(loaded);
        assertEquals("alice", loaded.getMember("user"));
        assertEquals(42, ((Number) loaded.getMember("count")).intValue());
    }

    @Test
    void testDelete() {
        Session session = store.create(600);
        String id = session.getId();

        store.delete(id);
        assertNull(store.get(id));
        assertFalse(Files.exists(tempDir.resolve("sessions").resolve(id + ".json")));
    }

    @Test
    void testExpiredSessionReturnsNull() {
        // Create session that expires in 1 second
        Session session = store.create(1);
        String id = session.getId();
        assertNotNull(store.get(id));

        // Force expiry by modifying the session's expires field (epoch seconds, in the past)
        session.setExpires(java.time.Instant.now().getEpochSecond() - 1);
        store.save(session);

        assertNull(store.get(id));
    }

    @Test
    void testGetNonexistent() {
        assertNull(store.get("nonexistent-id"));
        assertNull(store.get(null));
    }

    @Test
    void testDeleteNull() {
        // Should not throw
        store.delete(null);
        store.delete("nonexistent-id");
    }

    @Test
    void testSaveNull() {
        // Should not throw
        store.save(null);
    }

    @Test
    void testDirectoryCreated() {
        Path newDir = tempDir.resolve("new-sessions");
        assertFalse(Files.exists(newDir));
        new FileSessionStore(newDir);
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    void testSurvivesNewStoreInstance() {
        Session session = store.create(600);
        session.putMember("key", "value");
        store.save(session);
        String id = session.getId();

        // Create a new store instance pointing to the same directory
        FileSessionStore newStore = new FileSessionStore(tempDir.resolve("sessions"));
        Session loaded = newStore.get(id);
        assertNotNull(loaded);
        assertEquals("value", loaded.getMember("key"));
    }

    @Test
    void testNestedDataStructures() {
        Session session = store.create(600);
        session.putMember("profile", java.util.Map.of(
                "name", "Alice",
                "roles", java.util.List.of("admin", "user")
        ));
        store.save(session);

        Session loaded = store.get(session.getId());
        Object profile = loaded.getMember("profile");
        assertInstanceOf(java.util.Map.class, profile);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> profileMap = (java.util.Map<String, Object>) profile;
        assertEquals("Alice", profileMap.get("name"));
    }
}
