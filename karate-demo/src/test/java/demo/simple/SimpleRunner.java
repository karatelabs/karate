package demo.simple;

import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author jkeys089
 */
@RunWith(Karate.class)
public class SimpleRunner {

    @BeforeClass
    public static void beforeClass() {
        // skip 'callSingle' in karate-config.js
        System.setProperty("karate.env", "mock");
    }

}
