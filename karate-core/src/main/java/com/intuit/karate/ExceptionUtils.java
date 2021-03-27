package com.intuit.karate;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtils {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);


    public static String getStackTraceAsString(final Throwable throwable) {
        try(final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true)) {
            throwable.printStackTrace(pw);
            return sw.getBuffer().toString();
        } catch (IOException ex) {
            return null;
        }
    }
}
