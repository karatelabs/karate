package demo;

import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
public class DemoUtils {
    
    public static void copyFeatureFilesAndStartServer() {
        try {
            File srcDir = new File("../karate-demo/src/test/java");
            File destDir = new File("target/test-classes");
            FileUtils.copyDirectory(srcDir, destDir, f -> true, false);
            ConfigurableApplicationContext context = Application.run(new String[]{"--server.port=0"});
            ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
            System.setProperty("karate.env", "gatling");
            System.setProperty("demo.server.port", ss.getLocalPort() + "");
            System.setProperty("demo.server.https", "false");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }
    
}
