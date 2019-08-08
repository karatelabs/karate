package driver.core;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/core/test-03.feature")
public class Test03Runner {

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}
