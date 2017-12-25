package com.intuit.karate.netty;

import com.intuit.karate.FileUtils;
import com.intuit.karate.cucumber.CucumberRunner;
import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FeatureServerTest {
    
    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() {
        File file = FileUtils.getFileRelativeTo(FeatureServerTest.class, "server.feature");
        server = FeatureServer.start(file, 0, false);
        int port = server.getPort();
        System.setProperty("karate.server.port", port + "");        
    }

    @Test
    public void testServer() {
        CucumberRunner.runFeature(getClass(), "client.feature", null, true);
    }

}
