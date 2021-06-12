package com.intuit.karate.core.tags;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TagsTest {

    @Test
    public void testFeatureTag() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/outline-tags.feature")
                .tags("@featuretag")
                .parallel(1);
        assertEquals(4, results.getScenariosPassed());
    }

    @Test
    public void testOutlineTag() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/outline-tags.feature")
                .tags("@outlinetag")
                .parallel(1);
        assertEquals(4, results.getScenariosPassed());
    }

    @Test
    public void testOneTag() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/outline-tags.feature")
                .tags("@one")
                .parallel(1);
        assertEquals(2, results.getScenariosPassed());
    }

    @Test
    public void testOneAndBoth() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/outline-tags.feature")
                .tags("@one", "@both")
                .parallel(1);
        assertEquals(2, results.getScenariosPassed());
    }

    @Test
    public void testNoneOrBoth() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/outline-tags.feature")
                .tags("@none,@both")
                .parallel(1);
        assertEquals(4, results.getScenariosPassed());
    }

    @Test
    public void testEnvNotFoo() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/env-tags.feature")
                .parallel(1);
        assertEquals(1, results.getScenariosPassed());
        String name = results.getScenarioResults().findFirst().get().getScenario().getName();
        assertEquals("envnot=foo", name);
    }

    @Test
    public void testEnvFoo() {
        Results results = Runner.path("classpath:com/intuit/karate/core/tags/env-tags.feature")
                .karateEnv("foo")
                .parallel(1);
        assertEquals(1, results.getScenariosPassed());
        String name = results.getScenarioResults().findFirst().get().getScenario().getName();
        assertEquals("env=foo", name);
    }

}
