package jobtest.simple;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.job.MavenJobConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * use this to troubleshoot the job-server-executor flow
 * since this all runs locally and does not use a remote / docker instance
 * you can debug and view all the logs in one place
 * 
 * @author pthomas3
 */
public class SimpleLocalJobRunner {

    @Test
    void testJobManager() {
        MavenJobConfig config = new MavenJobConfig(-1, "127.0.0.1", 0) {
            @Override
            public void startExecutors(String uniqueId, String serverUrl) throws Exception {
                int executorCount = 2;
                ExecutorService executor = Executors.newFixedThreadPool(executorCount);
                for (int i = 0; i < executorCount; i++) {
                    executor.submit(() -> JobExecutor.run(serverUrl));
                }
                executor.shutdown();
                executor.awaitTermination(0, TimeUnit.MINUTES);
            }
        };
        // export KARATE_TEST="foo"
        config.addEnvPropKey("KARATE_TEST");
        Results results = Runner.path("classpath:jobtest/simple").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
