package jobtest.web;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.MavenChromeJobConfig;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class WebDockerJobRunner {

    @Test
    void test() {        
        MavenChromeJobConfig config = new MavenChromeJobConfig(2, "host.docker.internal", 0);
        System.setProperty("karate.env", "jobserver");
        Results results = Runner.path("classpath:jobtest/web").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
