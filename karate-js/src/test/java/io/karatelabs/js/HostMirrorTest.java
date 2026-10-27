package io.karatelabs.js;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import static io.karatelabs.js.HostMirror.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class HostMirrorTest {

    @Test
    void testKind() {
        assertEquals(NONE, HostMirror.kind(null));
        assertEquals(NONE, HostMirror.kind("a"));
        assertEquals(NONE, HostMirror.kind(1));
        assertEquals(NONE, HostMirror.kind(new HashMap<>()));
        assertEquals(NONE, HostMirror.kind(LocalTime.NOON)); // java.time, but no Date mirror
        assertEquals(UTIL_DATE, HostMirror.kind(new Date()));
        assertEquals(UTIL_DATE, HostMirror.kind(new java.sql.Timestamp(0)));
        assertEquals(TEMPORAL, HostMirror.kind(Instant.EPOCH));
        assertEquals(TEMPORAL, HostMirror.kind(LocalDate.EPOCH));
        assertEquals(TEMPORAL, HostMirror.kind(LocalDateTime.MIN));
        assertEquals(TEMPORAL, HostMirror.kind(ZonedDateTime.now()));
        assertEquals(TEMPORAL, HostMirror.kind(OffsetDateTime.now()));
        assertEquals(LIST, HostMirror.kind(new ArrayList<>()));
        assertEquals(LIST, HostMirror.kind(new JsArray())); // by Java type: callers test ObjectLike first
        assertEquals(BYTES, HostMirror.kind(new byte[0]));
        assertEquals(JAVA_ARRAY, HostMirror.kind(new int[0]));
        assertEquals(JAVA_ARRAY, HostMirror.kind(new String[0]));
    }

    @Test
    void testHostFirstSource() {
        Instant i = Instant.EPOCH;
        assertSame(i, HostMirror.hostFirstSource(i));
        assertSame(i, HostMirror.hostFirstSource(HostMirror.dateView(i)));
        assertNull(HostMirror.hostFirstSource(new Date()));
        assertNull(HostMirror.hostFirstSource(HostMirror.dateView(new Date())));
        assertNull(HostMirror.hostFirstSource(new JsDate(0)));
        assertNull(HostMirror.hostFirstSource(LocalTime.NOON));
    }

    @Test
    void testPlainJsDateHasNoJavaSource() {
        JsDate d = new JsDate(0);
        assertNull(d.getOriginalJavaValue());
        d.setTimeValue(1000);
        assertEquals(new Date(1000), d.getJavaValue());
        Date jud = new Date(0);
        JsDate view = HostMirror.dateView(jud);
        view.setTimeValue(1000);
        assertEquals(1000, jud.getTime());
        assertSame(jud, view.getJavaValue());
    }

}
