package mock.web;

import com.intuit.karate.FileUtils;
import com.intuit.karate.netty.FeatureServer;
import java.io.File;
import org.junit.Test;

/**
 *
 * @author pthomas3
 */
public class CatsMockStarter {

    @Test
    public  void beforeClass() {
        File file = FileUtils.getFileRelativeTo(getClass(), "cats-mock.feature");
        FeatureServer server = FeatureServer.start(file, 8080, false, null);
        server.waitSync();
    }

}
