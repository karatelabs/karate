package mock.micro;

import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.core.MockServer;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/micro/cats.feature")
public class CatsMockRunner {

    @BeforeClass
    public static void beforeClass() {
        MockServer server = MockServer
                .feature("classpath:mock/micro/cats-mock.feature")
                .arg("demoServerPort", null)
                .http(0).build();
        System.setProperty("karate.env", "mock");
        System.setProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats");
    }

}
