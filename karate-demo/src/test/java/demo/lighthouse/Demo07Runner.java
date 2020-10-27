
package demo.lighthouse;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:demo/lighthouse/demo-07.feature")
public class Demo07Runner {

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}