package demo.unit;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
public class UnitRunner {
    
    @BeforeClass
    public static void beforeClass() {
        // skip 'callSingle' in karate-config.js
        System.setProperty("karate.env", "mock"); 
    }    
    
}
