package com.intuit.karate.junit4.http;

import com.intuit.karate.FileUtils;
import com.intuit.karate.Http;
import com.intuit.karate.LogAppender;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class JavaHttpTest {

    private static FeatureServer server;

    @BeforeClass
    public static void beforeClass() {
        File file = FileUtils.getFileRelativeTo(JavaHttpTest.class, "server.feature");
        server = FeatureServer.start(file, 0, false, null);
    }

    @Test
    public void testHttp() {
        Http http = Http.forUrl(LogAppender.NO_OP, "http://localhost:" + server.getPort());
        http.path("echo").get().body().equals("{ uri: '/echo' }");
        String expected = "ws://127.0.0.1:9222/devtools/page/E54102F8004590484CC9FF85E2ECFCD0";
        http.path("chrome").get().body()
                .equalsText("#[1]")
                .jsonPath("get[0] $..webSocketDebuggerUrl")
                .equalsText(expected);
    }

    @AfterClass
    public static void afterClass() {
        server.stop();
    }

}
