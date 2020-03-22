package driver.demo;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/demo/demo-04.feature")
public class Demo04Runner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}