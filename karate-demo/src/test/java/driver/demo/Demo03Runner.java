package driver.demo;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/demo/demo-03.feature")
public class Demo03Runner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}