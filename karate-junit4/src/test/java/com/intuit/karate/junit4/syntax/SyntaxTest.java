package com.intuit.karate.junit4.syntax;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public class SyntaxTest {   
    
    @BeforeClass
    public static void before() {
        System.setProperty("karate.env", "foo");
    }

}
