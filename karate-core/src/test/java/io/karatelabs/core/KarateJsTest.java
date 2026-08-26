package io.karatelabs.core;

import io.karatelabs.common.Json;
import io.karatelabs.common.Resource;
import io.karatelabs.http.ErrorHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for KarateJs engine functionality.
 * Note: JS HTTP client tests are in ServerIntegrationTest to share the test server.
 */
class KarateJsTest {

    @Test
    void testJsClientNoServerConnection() {
        KarateJs context = new KarateJs(Resource.path(""), new ErrorHttpClient());
        String js = """
                var http = karate.http('http://localhost:99');
                var response = http.path('cats').post({ name: 'Billie' }).body;
                """;
        try {
            context.engine.eval(js);
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("expression: http.path('cats').post({name:'Billie'}) - failed"));
        }
    }

    @Test
    void testRead() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        String js = """
                var cat = read('json/cat.json');
                """;
        context.engine.eval(js);
        assertEquals(Map.of("name", "Billie", "age", 5), context.engine.get("cat"));
    }

    @Test
    void testReadScalarJsonString() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var token = read('json/scalar-string.json');");
        assertEquals("abc123", context.engine.get("token"));
    }

    @Test
    void testReadScalarJsonNumber() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var num = read('json/scalar-number.json');");
        assertEquals(42, context.engine.get("num"));
    }

    @Test
    void testReadScalarJsonBoolean() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        context.engine.eval("var flag = read('json/scalar-boolean.json');");
        assertEquals(true, context.engine.get("flag"));
    }

    @Test
    void testSysenv() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        // PATH is virtually always set on the platforms we ship to.
        context.engine.eval("var p = karate.sysenv('PATH');");
        Object p = context.engine.get("p");
        assertTrue(p instanceof String && !((String) p).isEmpty(),
                "karate.sysenv('PATH') should return the OS PATH");
        // Unset variable returns null.
        context.engine.eval("var missing = karate.sysenv('__KARATE_SHOULD_NEVER_BE_SET_98765__');");
        assertEquals(null, context.engine.get("missing"));
    }

    @Test
    void testSysenvDefault() {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        // Unset → default returned.
        context.engine.eval("var v = karate.sysenv('__KARATE_SHOULD_NEVER_BE_SET_98765__', 'fallback');");
        assertEquals("fallback", context.engine.get("v"));
        // Set → real value wins over default.
        context.engine.eval("var p = karate.sysenv('PATH', 'fallback');");
        Object p = context.engine.get("p");
        assertTrue(p instanceof String && !"fallback".equals(p),
                "real env var should win over default");
    }

    @Test
    void testReadOfficeBinaryExtensions() throws Exception {
        KarateJs context = new KarateJs(Resource.path("src/test/resources"));
        Path xlsx = Path.of("src/test/resources/io/karatelabs/core/upload/test.xlsx");
        byte[] expected = Files.readAllBytes(xlsx);

        context.engine.eval("""
                var bytes = read('io/karatelabs/core/upload/test.xlsx');
                var type = karate.typeOf(bytes);
                """);
        assertEquals("bytes", context.engine.get("type"));
        assertArrayEquals(expected, (byte[]) context.engine.get("bytes"));
    }

    @Test
    void testReadSniffsBinaryMagicForUnknownExtensions(@TempDir Path tempDir) throws Exception {
        // binary container formats whose extensions are not on the read() allowlist
        byte[] zip = Files.readAllBytes(Path.of("src/test/resources/io/karatelabs/core/upload/test.xlsx"));
        Files.write(tempDir.resolve("archive.odt"), zip);
        byte[] gzip = Files.readAllBytes(Path.of("src/test/resources/io/karatelabs/core/upload/gzip.bin"));
        Files.write(tempDir.resolve("data.tgz"), gzip);
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        Files.write(tempDir.resolve("legacy.msg"), ole);
        // control: text starting with "PK" but no zip magic must stay a string
        String text = "PK is a prefix, not a zip archive";
        Files.writeString(tempDir.resolve("notes.unknown"), text);

        KarateJs context = new KarateJs(Resource.path(tempDir.toString()));
        context.engine.eval("""
                var odt = read('archive.odt');
                var odtType = karate.typeOf(odt);
                var tgz = read('data.tgz');
                var tgzType = karate.typeOf(tgz);
                var msg = read('legacy.msg');
                var msgType = karate.typeOf(msg);
                var notes = read('notes.unknown');
                var notesType = karate.typeOf(notes);
                """);
        assertEquals("bytes", context.engine.get("odtType"));
        assertArrayEquals(zip, (byte[]) context.engine.get("odt"));
        assertEquals("bytes", context.engine.get("tgzType"));
        assertArrayEquals(gzip, (byte[]) context.engine.get("tgz"));
        assertEquals("bytes", context.engine.get("msgType"));
        assertArrayEquals(ole, (byte[]) context.engine.get("msg"));
        assertEquals("string", context.engine.get("notesType"));
        assertEquals(text, context.engine.get("notes"));
    }

    @Test
    void testSysprop() {
        System.setProperty("__karate_sysprop_test__", "hello");
        try {
            KarateJs context = new KarateJs(Resource.path("src/test/resources"));
            context.engine.eval("var v = karate.sysprop('__karate_sysprop_test__');");
            assertEquals("hello", context.engine.get("v"));
            // Unset → null.
            context.engine.eval("var u = karate.sysprop('__karate_sysprop_unset_99999__');");
            assertEquals(null, context.engine.get("u"));
            // Unset with default → default.
            context.engine.eval("var d = karate.sysprop('__karate_sysprop_unset_99999__', 'fallback');");
            assertEquals("fallback", context.engine.get("d"));
            // Set with default → real value wins.
            context.engine.eval("var w = karate.sysprop('__karate_sysprop_test__', 'fallback');");
            assertEquals("hello", context.engine.get("w"));
        } finally {
            System.clearProperty("__karate_sysprop_test__");
        }
    }

}
