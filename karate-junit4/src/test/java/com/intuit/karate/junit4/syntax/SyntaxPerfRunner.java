package com.intuit.karate.junit4.syntax;

import com.intuit.karate.cucumber.CucumberRunner;
import java.util.Collections;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class SyntaxPerfRunner {
    
    @Test
    public void testPerf() {
        System.setProperty("karate.env", "foo");
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            CucumberRunner.runFeature(getClass(), "syntax.feature", Collections.EMPTY_MAP, true); 
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("elapsed time: " + elapsedTime);
        // 25.5 seconds for git 76c92bd
        // 14.0 seconds after refactoring
    }
    
    public static void main(String[] args) {
        new SyntaxPerfRunner().testPerf();
    }
    
}
