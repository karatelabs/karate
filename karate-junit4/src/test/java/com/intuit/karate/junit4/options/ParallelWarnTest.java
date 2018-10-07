package com.intuit.karate.junit4.options;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.junit4.Karate;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public class ParallelWarnTest {
    
    @Test
    public void testParallel() {
        Results results = Runner.parallel(getClass(), 1);
    }
    
}
