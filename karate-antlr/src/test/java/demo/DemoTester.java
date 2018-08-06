package demo;

import com.intuit.karate.CallContext;
import com.intuit.karate.FileResource;
import com.intuit.karate.FileUtils;
import com.intuit.karate.ScriptEnv;
import com.intuit.karate.StepDefs;
import com.intuit.karate.core.Engine;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureParser;
import com.intuit.karate.demo.Application;
import com.intuit.karate.demo.config.ServerStartedInitializingBean;
import cucumber.api.CucumberOptions;
import java.io.File;
import java.util.List;
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
        org.apache.commons.io.FileUtils.copyDirectory(srcDir, destDir, f -> true, false);
        ConfigurableApplicationContext context = Application.run(new String[]{"--server.port=0"});
        ServerStartedInitializingBean ss = context.getBean(ServerStartedInitializingBean.class);
        System.setProperty("karate.env", "engine");
        System.setProperty("demo.server.port", ss.getLocalPort() + "");
        System.setProperty("demo.server.https", "false");
        List<FileResource> files = FileUtils.scanForFeatureFiles("classpath:demo");
        for (FileResource file : files) {            
            ScriptEnv env = ScriptEnv.forEnvTagsAndFeatureFile(null, "not('@ignore')", file.file);
            Feature feature = FeatureParser.parse(file.file, file.relativePath);
            StepDefs stepDefs = new StepDefs(env, new CallContext(null, true));
            Engine.execute(feature, stepDefs);
        }

    }

}
