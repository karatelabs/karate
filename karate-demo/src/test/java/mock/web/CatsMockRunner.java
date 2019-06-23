package mock.web;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/web/cats-test.feature")
public class CatsMockRunner {

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "mock");
    }

}
