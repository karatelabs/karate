package com.intuit.karate.junit5;

import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleTest {

    private static final Logger logger = LoggerFactory.getLogger(SampleTest.class);

    @TestFactory
    public Object test() {
        return Karate.withFeatures("sample.feature").relativeTo(getClass()).run();
    }

}