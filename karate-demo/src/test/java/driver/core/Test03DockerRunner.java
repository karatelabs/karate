package driver.core;

import com.intuit.karate.FileUtils;
import com.intuit.karate.junit4.Karate;
import com.intuit.karate.KarateOptions;
import com.intuit.karate.netty.FeatureServer;
import com.intuit.karate.shell.Command;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author pthomas3
 */
@RunWith(Karate.class)
@KarateOptions(features = "classpath:driver/core/test-03.feature")
public class Test03DockerRunner {
    
    private static Command command;
    
    @BeforeClass
    public static void beforeClass() {
        File file = FileUtils.getFileRelativeTo(Test03DockerRunner.class, "_mock.feature");
        FeatureServer server = FeatureServer.start(file, 0, false, null);
        System.setProperty("karate.env", "mock");
        System.setProperty("web.url.base", "http://host.docker.internal:" + server.getPort());
        String line = "docker run --rm -e KARATE_SOCAT_START=true --cap-add=SYS_ADMIN -p 9222:9222 ptrthomas/karate-chrome";
        command = new Command(true, null, Command.tokenize(line));
        command.start();
    }
    
    @AfterClass
    public static void afterClass() {
        command.close(false);
    }

}
