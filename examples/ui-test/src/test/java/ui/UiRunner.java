package ui;

import com.intuit.karate.FileUtils;
import com.intuit.karate.junit5.Karate;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.jupiter.api.BeforeAll;

class UiRunner {
    
    @BeforeAll
    public static void beforeAll() {
        File file = FileUtils.getFileRelativeTo(UiRunner.class, "mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("web.url.base", "http://localhost:" + server.getPort());        
    }
    
    @Karate.Test
    Karate testUi() {
        return Karate.run("classpath:ui/test.feature");
    }
    
}
