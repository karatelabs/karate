package driver.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class MockRunner {
    
    @Test
    public void testStart() {
        File file = FileUtils.getFileRelativeTo(MockRunner.class, "_mock.feature");
        FeatureServer server = FeatureServer.start(file, 8080, false, null);    
        server.waitSync();
    }
    
}
