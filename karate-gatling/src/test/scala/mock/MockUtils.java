package mock;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class MockUtils {
    
    public static void startServer() {
        File file = FileUtils.getFileRelativeTo(MockUtils.class, "cats-mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("mock.cats.url", "http://localhost:" + server.getPort() + "/cats");        
    }
    
}
