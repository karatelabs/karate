package demo;

import com.intuit.karate.cucumber.CucumberRunner;
import com.intuit.karate.cucumber.KarateStats;
import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import cucumber.api.CucumberOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(tags = {"~@ignore"})
public class DemoTester {
    
    @Test
    public void testDemoSuite() throws Exception {
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        FileUtils.copyDirectory(srcDir, destDir, f -> true, false);
        ConfigurableApplicationContext context = Application.run(new String[]{"--server.port=0"});
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        System.setProperty("karate.env", "engine");
        System.setProperty("demo.server.port", ss.getLocalPort() + "");
        System.setProperty("demo.server.https", "false");
        KarateStats stats = CucumberRunner.parallel(getClass(), 5);
        assertTrue("there are scenario failures", stats.getFailCount() == 0);
    }
    
}
