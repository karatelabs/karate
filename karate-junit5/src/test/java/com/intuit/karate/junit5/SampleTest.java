package com.intuit.karate.junit5;

import org.junit.jupiter.api.TestFactory;

public class SampleTest {

    @TestFactory
    public Object test() {
        return Karate.withFeatures("sample.feature").relativeTo(getClass()).run();
    }

}