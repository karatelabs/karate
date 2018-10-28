package com.intuit.karate.junit5;

import org.junit.jupiter.api.TestFactory;

public class SampleTest {

    @TestFactory
    public Object testSample() {
        return Karate.feature("sample").relativeTo(getClass()).run();
    }
    
    @TestFactory
    public Object testTags() {
        return Karate.feature("tags").tags("@second").relativeTo(getClass()).run();
    }

    @TestFactory
    public Object testFullPath() {
        return Karate
                .feature("classpath:com/intuit/karate/junit5/tags.feature")
                .tags("@first").run();
    }

}