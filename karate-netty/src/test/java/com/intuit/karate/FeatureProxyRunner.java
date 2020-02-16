package com.intuit.karate;

import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class FeatureProxyRunner {
    
    @Test
    public void testServer() {
        File file = FileUtils.getFileRelativeTo(FeatureProxyRunner.class, "proxy.feature");
        FeatureServer server = FeatureServer.start(file, 8090, false, null);
        server.waitSync();
    }
    
}
