package driver.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
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
        File file = FileUtils.getFileRelativeTo(Test04Runner.class, "_mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("karate.env", "mock");
        System.setProperty("web.url.base", "http://localhost:" + server.getPort());
    }

}
