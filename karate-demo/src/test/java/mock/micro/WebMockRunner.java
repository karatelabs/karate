package mock.micro;

import com.intuit.karate.FileUtils;
import com.intuit.karate.junit4.Karate;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.KarateOptions;
import java.io.File;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:mock/micro/web.feature")
public class WebMockRunner {
    
    @BeforeClass
    public static void testCats() {
        File file = FileUtils.getFileRelativeTo(WebMockRunner.class, "../web/cats-mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("karate.env", "mock");
        System.setProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats");
    }
    
}
