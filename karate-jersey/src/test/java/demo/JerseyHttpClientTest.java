package demo;

import com.intuit.karate.Runner;
import com.intuit.karate.Results;
import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import com.intuit.karate.KarateOptions;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 *
 * @author pthomas3
 */
@KarateOptions(tags = {"~@ignore", "~@apache"})
public class JerseyHttpClientTest {
    
    @Test
    public void testJerseyClient() throws Exception {
        File srcDir = new File("../karate-demo/src/test/java");
        File destDir = new File("target/test-classes");
        FileUtils.copyDirectory(srcDir, destDir, f -> true, false);
        ConfigurableApplicationContext context = Application.run(new String[]{"--server.port=0"});
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        System.setProperty("karate.env", "jersey");
        System.setProperty("demo.server.port", ss.getLocalPort() + "");
        System.setProperty("demo.server.https", "false");
        Results results = Runner.parallel(getClass(), 5);
        assertTrue("there are scenario failures", results.getFailCount() == 0);
    }
    
}
