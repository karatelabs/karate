package io.karatelabs.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
        // Force the session into the expired state (epoch seconds in the past).
        // Don't rely on Thread.sleep — seconds-resolution would need a >1s pause.
        Session session = store.create(60);
        String id = session.getId();
        session.setExpires(Instant.now().getEpochSecond() - 1);
        store.save(session);

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
        long before = Instant.now().getEpochSecond();
        Session session = store.create(expirySeconds);
        long after = Instant.now().getEpochSecond();

        long expectedMinExpiry = before + expirySeconds;
        long expectedMaxExpiry = after + expirySeconds;

        assertTrue(session.getExpires() >= expectedMinExpiry);
        assertTrue(session.getExpires() <= expectedMaxExpiry);
    }

    /**
     * Locks in the framework's session-time unit contract: {@code created /
     * updated / expires} are <b>epoch seconds</b>, not milliseconds. A
     * regression to milliseconds would silently break any persistence layer
     * relying on it as a TTL — most notably DynamoDB's
     * {@code TimeToLiveSpecification}, which interprets the attribute as
     * seconds and silently no-ops on millisecond values (treating them as
     * dates ~58,000 years in the future).
     *
     * <p>This was the legacy karate-core 1.5.0 contract; the post-rewrite
     * implementation initially regressed to milliseconds, which is what this
     * test guards against.</p>
     */
    @Test
    void testSessionTimesAreEpochSeconds() {
        long nowSec = Instant.now().getEpochSecond();
        Session session = store.create(600);

        // Each field should be a 10-digit epoch-seconds value, not a 13-digit ms value.
        // 1e11 = ~year 5138 in seconds, but only ~year 1973 in ms — so anything
        // in the current decade is well below 1e11 if it's seconds, and well
        // above if it's ms. This bound is robust through ~year 5000.
        long secondsUpperBound = 100_000_000_000L; // 1e11
        assertTrue(session.getCreated() < secondsUpperBound,
                "getCreated() must be epoch seconds, not millis (got " + session.getCreated() + ")");
        assertTrue(session.getUpdated() < secondsUpperBound,
                "getUpdated() must be epoch seconds, not millis (got " + session.getUpdated() + ")");
        assertTrue(session.getExpires() < secondsUpperBound,
                "getExpires() must be epoch seconds, not millis (got " + session.getExpires() + ")");

        // And the values should be consistent with "now" in seconds.
        assertTrue(Math.abs(session.getCreated() - nowSec) <= 2);
        assertEquals(session.getCreated() + 600, session.getExpires());
    }

}
