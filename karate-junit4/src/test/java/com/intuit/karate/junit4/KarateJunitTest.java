package com.intuit.karate.junit4;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.BeforeClass;

/**
 *
 * @author pthomas3
 */
public class KarateJunitTest {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("custom_tags", "test, requirement");
        System.setProperty("custom_xml_tags", "test_key, requirement");
    }

    @Test
    public void testAll() {
        Results results = Runner
                .path("classpath:com/intuit/karate/junit4/demos")
                .path("classpath:com/intuit/karate/junit4/files")
                .path("classpath:com/intuit/karate/junit4/xml")
                .parallel(5);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());
    }    

    @Test
    public void testCustomTags() {
        Results results = Runner
                .path("classpath:com/intuit/karate/junit4/customTags")
                .outputJunitXml(true)
                .parallel(1);
        assertEquals(results.getErrorMessages(), 0, results.getFailCount());
    }    
    
}
