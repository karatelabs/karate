package io.karatelabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code ConfigLoader.evaluate}: beside the config variables, the auth the config configures, as its type and the
 * names of the fields it set, never a value.
 */
class ConfigLoaderAuthTest {

    static final String NAME = "__KARATE_CONFIG_AUTH_TEST__";

    @TempDir
    Path dir;

    @Test
    void handWrittenAuthAnswersItsTypeAndFieldNames() throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), """
                function fn() {
                  karate.configure('auth', { type: 'oauth2', accessTokenUrl: 'http://localhost/token',
                    clientId: 'app', clientSecret: karate.sysenv('%s') });
                  return { baseUrl: 'http://localhost' };
                }
                """.formatted(NAME));
        ConfigLoader.Evaluated evaluated = ConfigLoader.evaluate(dir, null, null, Map.of(NAME, "s3cr3t"));
        assertEquals(Map.of("baseUrl", "http://localhost"), evaluated.vars());
        assertEquals(Map.of("type", "oauth2", "fields", List.of("accessTokenUrl", "clientId", "clientSecret"),
                "refs", Map.of("clientSecret", NAME)), evaluated.auth());
    }

    @Test
    void noValueOfAFieldOrTheEnvironmentIsAnswered() throws Exception {
        writeBasic();
        Map<String, String> env = Map.of("APP_USER", "alice-user", "APP_PASSWORD", "p4ssw0rd", "OTHER", "unrelated-value");
        String auth = io.karatelabs.common.Json.stringifyStrict(ConfigLoader.evaluate(dir, null, null, env).auth());
        for (String value : List.of("alice-user", "p4ssw0rd", "unrelated-value", "literal-pass")) {
            assertFalse(auth.contains(value), auth);
        }
    }

    @Test
    void aFieldWhoseValueNoNameHoldsHasNoRef() throws Exception {
        writeBasic();
        Map<String, Object> auth = ConfigLoader.evaluate(dir, null, null, Map.of("APP_USER", "alice-user")).auth();
        assertEquals(Map.of("username", "APP_USER"), auth.get("refs"));
        assertEquals(Map.of(), ConfigLoader.evaluate(dir, null, null, null).auth().get("refs"));
    }

    @Test
    void aValueSeveralNamesHoldRefsTheFirstName() throws Exception {
        writeBasic();
        Map<String, String> env = Map.of("Z_USER", "alice-user", "APP_USER", "alice-user", "M_USER", "alice-user");
        assertEquals(Map.of("username", "APP_USER"), ConfigLoader.evaluate(dir, null, null, env).auth().get("refs"));
    }

    private void writeBasic() throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), """
                function fn() {
                  karate.configure('auth', { type: 'basic', username: karate.sysenv('APP_USER') || 'nobody',
                    password: 'literal-pass' });
                  return {};
                }
                """);
    }

    @Test
    void noAuthConfiguredAnswersAnEmptyMap() throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), "function fn() { return { a: 1 }; }");
        assertTrue(ConfigLoader.evaluate(dir, null, null, null).auth().isEmpty());
        assertTrue(ConfigLoader.evaluate(null, null, null, null).auth().isEmpty());
    }

}
