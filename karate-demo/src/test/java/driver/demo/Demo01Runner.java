package driver.demo;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/demo/demo-01.feature")
public class Demo01Runner {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}