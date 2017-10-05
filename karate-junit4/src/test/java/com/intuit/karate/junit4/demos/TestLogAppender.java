package com.intuit.karate.junit4.demos;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.intuit.karate.FileUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class TestLogAppender extends AppenderBase<ILoggingEvent> {

    private final Logger logger;
    private final PatternLayoutEncoder encoder;
    private StringBuilder sb;

    public TestLogAppender() {
        sb = new StringBuilder();
        logger = (Logger) LoggerFactory.getLogger("com.intuit.karate");
        setName("karate-test");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(lc);
        encoder = new PatternLayoutEncoder();
        encoder.setPattern("%msg");
        encoder.setContext(context);
        encoder.start();
        start();
        logger.addAppender(this);
        logger.setLevel(Level.DEBUG);
    }

    public String collect() {
        String temp = sb.toString();
        sb = new StringBuilder();
        return temp.replace("\r\n", "\n"); // fix for windows
    }

    @Override
    protected void append(ILoggingEvent event) {
        byte[] bytes = encoder.encode(event);
        String line = FileUtils.toString(bytes);
        sb.append(line).append('\n');
    }

}
