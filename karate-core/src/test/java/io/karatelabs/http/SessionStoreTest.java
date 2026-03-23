package io.karatelabs.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

    private InMemorySessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore();
    }

    @Test
    void testCreate() {
        Session session = store.create(600);

        assertNotNull(session);
        assertNotNull(session.getId());
        assertFalse(session.getId().isEmpty());
        assertTrue(session.getCreated() > 0);
        assertTrue(session.getUpdated() > 0);
        assertTrue(session.getExpires() > session.getCreated());
        assertEquals(1, store.size());
    }

    @Test
    void testCreateMultipleSessions() {
        Session session1 = store.create(600);
        Session session2 = store.create(600);
        Session session3 = store.create(600);

        assertNotEquals(session1.getId(), session2.getId());
        assertNotEquals(session2.getId(), session3.getId());
        assertEquals(3, store.size());
    }

    @Test
    void testGet() {
        Session created = store.create(600);
        String id = created.getId();

        Session retrieved = store.get(id);

        assertNotNull(retrieved);
        assertEquals(id, retrieved.getId());
        assertSame(created, retrieved);
    }

    @Test
    void testGetNonExistent() {
        Session session = store.get("non-existent-id");
        assertNull(session);
    }

    @Test
    void testGetNullId() {
        Session session = store.get(null);
        assertNull(session);
    }

    @Test
    void testSave() {
        Session session = store.create(600);
        session.putMember("user", "john");

        long originalUpdated = session.getUpdated();
        // Small delay to ensure updated timestamp changes
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        store.save(session);

        Session retrieved = store.get(session.getId());
        assertEquals("john", retrieved.getMember("user"));
        assertTrue(retrieved.getUpdated() >= originalUpdated);
    }

    @Test
    void testSaveNull() {
        // Should not throw
        store.save(null);
        assertEquals(0, store.size());
    }

    @Test
    void testSaveTemporarySession() {
        // Temporary session has null ID, should not be saved
        store.save(Session.TEMPORARY);
        assertEquals(0, store.size());
    }

    @Test
    void testDelete() {
        Session session = store.create(600);
        String id = session.getId();
        assertEquals(1, store.size());

        store.delete(id);

        assertNull(store.get(id));
        assertEquals(0, store.size());
    }

    @Test
    void testDeleteNonExistent() {
        // Should not throw
        store.delete("non-existent-id");
    }

    @Test
    void testDeleteNull() {
        // Should not throw
        store.delete(null);
    }

    @Test
    void testExpiredSessionNotReturned() {
        // Create session with 1ms expiry
        Session session = store.create(0);
        String id = session.getId();

        // Wait for expiry
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }

        // Session should be expired and not returned
        Session retrieved = store.get(id);
        assertNull(retrieved);
    }

    @Test
    void testSessionDataPersists() {
        Session session = store.create(600);
        session.putMember("key1", "value1");
        session.putMember("key2", 123);
        session.putMember("key3", true);

        Session retrieved = store.get(session.getId());

        assertEquals("value1", retrieved.getMember("key1"));
        assertEquals(123, retrieved.getMember("key2"));
        assertEquals(true, retrieved.getMember("key3"));
    }

    @Test
    void testClear() {
        store.create(600);
        store.create(600);
        store.create(600);
        assertEquals(3, store.size());

        store.clear();

        assertEquals(0, store.size());
    }

    @Test
    void testExpiryCalculation() {
        int expirySeconds = 300; // 5 minutes
        long before = System.currentTimeMillis();
        Session session = store.create(expirySeconds);
        long after = System.currentTimeMillis();

        long expectedMinExpiry = before + (expirySeconds * 1000L);
        long expectedMaxExpiry = after + (expirySeconds * 1000L);

        assertTrue(session.getExpires() >= expectedMinExpiry);
        assertTrue(session.getExpires() <= expectedMaxExpiry);
    }

}
