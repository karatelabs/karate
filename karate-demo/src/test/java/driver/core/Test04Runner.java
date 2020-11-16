package driver.core;

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
@KarateOptions(features = "classpath:driver/core/test-04.feature")
public class Test04Runner {
    
    @BeforeClass
    public static void beforeClass() {
        MockServer server = MockServer.feature("classpath:driver/core/_mock.feature").http(8080).build();
        System.setProperty("karate.env", "mock");
        System.setProperty("web.url.base", "http://localhost:" + server.getPort());
    }

}
