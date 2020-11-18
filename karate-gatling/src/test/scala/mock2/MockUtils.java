package mock2;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;

import java.io.File;

class MockUtils {

    static void startServer() {
        File file = FileUtils.getFileRelativeTo(MockUtils.class, "mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("mock.cats.url", server.getBaseUrl() + "/cats");
    }
}
