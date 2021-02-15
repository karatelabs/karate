package driver.mock;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/mock/demo-01.feature")
public class Demo01Runner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }    
    
}
