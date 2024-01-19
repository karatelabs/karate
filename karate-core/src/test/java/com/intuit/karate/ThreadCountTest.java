package com.intuit.karate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intuit.karate.Constants.KARATE_OPTIONS;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThreadCountTest {

  private static final int THREAD_COUNT = 20;

  @AfterEach
  void clearKarateOptionsProperty() {
    System.clearProperty(KARATE_OPTIONS);
  }

  @Test
  void testThreadCountFromRunner() {
    Runner.Builder<?> builder = Runner.builder();
    builder.path("does-not-exist").parallel(THREAD_COUNT);
    assertEquals(builder.threadCount, THREAD_COUNT);
  }

  @Test
  void testThreadCountFromKarateOptionsShortName() {
    System.setProperty(KARATE_OPTIONS, "-T" + THREAD_COUNT);
    Runner.Builder<?> builder = Runner.builder();
    builder.path("does-not-exist").parallel(1);
    assertEquals(builder.threadCount, THREAD_COUNT);
  }

  @Test
  void testThreadCountFromKarateOptionsLongName() {
    System.setProperty(KARATE_OPTIONS, "--threads=" + THREAD_COUNT);
    Runner.Builder<?> builder = Runner.builder();
    builder.path("does-not-exist").parallel(1);
    assertEquals(builder.threadCount, THREAD_COUNT);
  }

  @Test
  void testThreadCountFromRunnerAndKarateOptionsWithoutThreadOption() {
    System.setProperty(KARATE_OPTIONS, "--tags=@does-not-exist");
    Runner.Builder<?> builder = Runner.builder();
    builder.path("does-not-exist").parallel(THREAD_COUNT);
    assertEquals(builder.threadCount, THREAD_COUNT);
  }
}
