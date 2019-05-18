package com.intuit.karate;

import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FeatureServerRunner {
    
    @Test
    public void testServer() {
        File file = FileUtils.getFileRelativeTo(FeatureServerRunner.class, "server.feature");
        FeatureServer server = FeatureServer.start(file, 8080, false, null);
        server.waitSync();
    }
    
}
