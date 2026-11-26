package io.karatelabs.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.karatelabs.common.Json;
import io.karatelabs.http.HttpResponse;
import io.karatelabs.http.HttpServer;
import io.karatelabs.output.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code ConfigLoader.evaluate}: the resolved auth reached only through {@code ConfigLoader.resolvedAuth}, and the quiet mode
 * that keeps every line the evaluation logs, a called login feature's included, out of the log.
 */
class ConfigLoaderQuietTest {

    static final List<String> LOGGERS = List.of("karate.scenario", "karate.http", "karate.runtime", "io.karatelabs");

    @TempDir
    Path dir;

    HttpServer server;
    ListAppender<ILoggingEvent> appender;
    final Map<String, Level> levels = new LinkedHashMap<>();

    @BeforeEach
    void beforeEach() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
        for (String name : LOGGERS) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            levels.put(name, logger.getLevel());
            logger.setLevel(Level.TRACE);
        }
        server = HttpServer.start(0, request -> HttpResponse.json("{\"token\":\"MINTED-SECRET\"}"));
        appender.list.clear();
    }

    @AfterEach
    void afterEach() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAppender(appender);
        levels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
        server.stop();
    }

    void writeLogin(String extraStep) throws Exception {
        Files.writeString(dir.resolve("login.feature"), """
                Feature: login

                Scenario: login
                * print 'PRINTED-SECRET'
                * karate.log('LOGGED-SECRET')
                * url 'http://localhost:%d'
                * method get
                * def token = response.token
                %s
                """.formatted(server.getPort(), extraStep));
        Files.writeString(dir.resolve("karate-config.js"), """
                function fn() {
                  var login = karate.call('file:%s');
                  karate.configure('auth', { type: 'bearer', token: login.token });
                  return { baseUrl: 'http://localhost' };
                }
                """.formatted(dir.resolve("login.feature").toString().replace("\\", "/")));
    }

    List<String> thisThreadsEvents() {
        String thread = Thread.currentThread().getName();
        return appender.list.stream().filter(e -> thread.equals(e.getThreadName())).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void theResolvedAuthIsReachedOnlyThroughItsOwnMethod() throws Exception {
        writeLogin("");
        ConfigLoader.Evaluated evaluated = ConfigLoader.evaluate(dir, null, null, null, true);
        ConfigLoader.ResolvedAuth resolved = ConfigLoader.resolvedAuth(evaluated);
        assertEquals("bearer", resolved.type());
        assertEquals("MINTED-SECRET", resolved.map().get("token"));
        assertEquals("ResolvedAuth[bearer]", resolved.toString());
        assertFalse(evaluated.toString().contains("MINTED-SECRET"), evaluated.toString());
        assertFalse(Json.stringifyStrict(evaluated.auth()).contains("MINTED-SECRET"));
        assertEquals(Map.of("type", "bearer", "fields", List.of("token"), "refs", Map.of()), evaluated.auth());
        assertThrows(UnsupportedOperationException.class, () -> resolved.map().put("token", "x"));
    }

    @Test
    void noAuthConfiguredHasNoResolvedAuth() throws Exception {
        Files.writeString(dir.resolve("karate-config.js"), "function fn() { return { a: 1 }; }");
        assertNull(ConfigLoader.resolvedAuth(ConfigLoader.evaluate(dir, null, null, null)));
        assertNull(ConfigLoader.resolvedAuth(ConfigLoader.evaluate(null, null, null, null)));
    }

    @Test
    void aQuietEvaluationLogsNothingAndRestoresTheThreadsContext() throws Exception {
        writeLogin("");
        LogContext before = LogContext.get();
        before.log("kept");
        ConfigLoader.evaluate(dir, null, null, null, true);
        assertSame(before, LogContext.get());
        assertEquals("kept\n", before.peek());
        assertFalse(LogContext.isQuiet());
        assertEquals(List.of(), thisThreadsEvents());
        before.collect();
    }

    @Test
    void anEvaluationThatIsNotQuietLogsAsBefore() throws Exception {
        writeLogin("");
        ConfigLoader.evaluate(dir, null, null, null, false);
        String all = String.join("\n", thisThreadsEvents());
        assertTrue(all.contains("PRINTED-SECRET") && all.contains("LOGGED-SECRET") && all.contains("MINTED-SECRET"), all);
        LogContext.get().collect();
    }

    @Test
    void aQuietEvaluationThatFailsStillThrowsAndLogsNothing() throws Exception {
        writeLogin("* match token == 'FAILED-SECRET'");
        assertThrows(RuntimeException.class, () -> ConfigLoader.evaluate(dir, null, null, null, true));
        assertEquals(List.of(), thisThreadsEvents());
        assertFalse(LogContext.isQuiet());
    }


    @Test
    void nothingAnEvaluatedOffersYieldsTheResolvedAuth() {
        for (java.lang.reflect.Method m : ConfigLoader.Evaluated.class.getMethods()) {
            assertNotEquals(ConfigLoader.ResolvedAuth.class, m.getReturnType(), m.toString());
        }
    }

}
