package driver.demo;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/demo/demo-05.feature")
public class Demo05Runner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}