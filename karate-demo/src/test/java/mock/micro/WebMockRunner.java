package mock.micro;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.netty.FeatureServer;
import cucumber.api.CucumberOptions;
import java.io.File;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:mock/micro/web.feature")
public class WebMockRunner {
    
    @Test
    public void testCats() {
        File file = FileUtils.getFileRelativeTo(getClass(), "../web/cats-mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("karate.env", "mock");
        System.setProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats");
        KarateStats stats = CucumberRunner.parallel(getClass(), 1, "target/web/cats-mock");
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
        server.stop();
    }
    
}
