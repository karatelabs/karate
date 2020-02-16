package ui;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * run this as a junit test to start an http server at port 8080
 * the html page can be viewed at http://localhost:8080/page-01
 * kill / stop this process when done
 */
class MockRunner {
    
    @Test
    public void testStart() {    
        File file = FileUtils.getFileRelativeTo(MockRunner.class, "mock.feature");
        FeatureServer server = FeatureServer.start(file, 8080, false, null);    
        server.waitSync();        
    }
    
}
