package com.intuit.karate.core.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

public class GreetTest {

  @Test
  public void testMethod() {
    Results results = Runner.path("classpath:com/intuit/karate/core/extensions/greet.feature")
        .parallel(1);
    assertEquals(0, results.getFailCount(), results.getErrorMessages());
  }

}
