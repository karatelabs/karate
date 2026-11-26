package io.karatelabs.output;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@link LogContext#quiet()}: this thread logs nothing while it is open; every other thread logs as before. */
class LogContextQuietTest {

    ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void beforeEach() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void afterEach() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
    }

    List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void aQuietThreadLogsNothingWhileAnotherThreadLogs() throws Exception {
        org.slf4j.Logger logger = LoggerFactory.getLogger("io.karatelabs.quiet.test");
        LogContext outer = LogContext.get();
        try (LogContext.Quiet ignored = LogContext.quiet()) {
            assertTrue(LogContext.isQuiet());
            logger.error("quiet-thread");
            LogContext.with(LogContext.SCENARIO_LOGGER).error("quiet-writer");
            LogContext.get().log(LogLevel.ERROR, "quiet-buffer");
            assertEquals("", LogContext.get().peek());
            Thread other = new Thread(() -> logger.error("other-thread"));
            other.start();
            other.join();
        }
        assertFalse(LogContext.isQuiet());
        assertSame(outer, LogContext.get());
        logger.error("after");
        assertEquals(List.of("other-thread", "after"), messages());
    }

    @Test
    void quietHandlesNest() {
        org.slf4j.Logger logger = LoggerFactory.getLogger("io.karatelabs.quiet.test");
        try (LogContext.Quiet outer = LogContext.quiet()) {
            try (LogContext.Quiet inner = LogContext.quiet()) {
                logger.error("inner");
            }
            assertTrue(LogContext.isQuiet());
            logger.error("outer");
        }
        logger.error("after");
        assertEquals(List.of("after"), messages());
    }

    @Test
    void theHttpLogFilterRewritesOrDropsALine() {
        LogContext ctx = new LogContext();
        List<String> seen = new ArrayList<>();
        ctx.setHttpLogFilter(line -> {
            seen.add(line);
            return line.contains("drop") ? null : line.replace("secret", "***");
        });
        assertEquals("a *** b", ctx.filterHttpLog("a secret b"));
        assertNull(ctx.filterHttpLog("drop me"));
        ctx.setHttpLogFilter(null);
        assertEquals("a secret b", ctx.filterHttpLog("a secret b"));
        assertEquals(2, seen.size());
    }

}
