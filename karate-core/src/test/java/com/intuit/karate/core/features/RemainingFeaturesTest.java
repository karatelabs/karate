package com.intuit.karate.core.features;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.Suite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemainingFeaturesTest {

  private static Suite suite;

  @Test
  void testRemainingFeaturesSingleThread() {
    verifyRemainingFeaturesWithThreads(1);
  }

  @Test
  void testRemainingFeaturesParallel() {
    verifyRemainingFeaturesWithThreads(2);
  }

  /**
   * Hooks into the current suite to return the remaining features within the test
   * @return Remaining features count
   */
  public static long remainingFeatures() {
    return suite.getFeaturesRemaining();
  }

  private void verifyRemainingFeaturesWithThreads(int threads) {
    Runner.Builder<?> builder = Runner.builder()
        .path("classpath:com/intuit/karate/core/features")
        .configDir("classpath:com/intuit/karate/core/features")
        .threads(threads);
    builder.resolveAll();
    suite = new Suite(builder);
    suite.run();
    Results results = suite.buildResults();
    assertEquals(0, results.getFailCount(), results.getErrorMessages());
  }

}
