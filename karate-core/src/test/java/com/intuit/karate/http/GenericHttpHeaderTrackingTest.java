package com.intuit.karate.http;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenericHttpHeaderTrackingTest {

    private GenericHttpHeaderTracking httpHeaderTracking;

    @BeforeEach
    void beforeEach() {
        httpHeaderTracking = new GenericHttpHeaderTracking();
    }

    @Test
    void testPutHeader() {
        String header = "X-Special-Header";

        Assertions.assertDoesNotThrow(() -> httpHeaderTracking.putHeaderReference(header));
    }

    @Test
    void testPutHeaderWithNull() {
        String header = null;

        Assertions.assertDoesNotThrow(() -> httpHeaderTracking.putHeaderReference(header));
    }

    @Test
    void testGetOriginalHeader() {
        String header = "X-Special-Header";
        httpHeaderTracking.putHeaderReference(header);

        String result = httpHeaderTracking.getOriginalHeader(header);
        Assertions.assertEquals(header, result);
    }

    @Test
    void testGetOriginalHeaderWithoutExistingHeaderInTracking() {
        String header = "X-Special-Header";

        String result = httpHeaderTracking.getOriginalHeader(header);
        Assertions.assertEquals(header, result);
    }

    @Test
    void testGetOriginalHeaderWithNull() {
        String header = null;

        String result = httpHeaderTracking.getOriginalHeader(header);
        Assertions.assertNull(result);
    }
}
