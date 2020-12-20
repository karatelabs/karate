package jobtest.web;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.MavenChromeJobConfig;
import com.intuit.karate.shell.Command;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class WebDockerJobRunner {

    @Test
    void test() {
        String mavenHome = System.getProperty("user.home") + File.separator + ".m2";
        MavenChromeJobConfig config = new MavenChromeJobConfig(2, "host.docker.internal", 0);
        config.setAddOptions("-v " + mavenHome + ":/root/.m2");
        System.setProperty("karate.env", "jobserver");
        Results results = Runner.path("classpath:jobtest/web").outputCucumberJson(true).jobManager(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
