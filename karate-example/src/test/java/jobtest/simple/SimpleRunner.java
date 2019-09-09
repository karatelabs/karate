package jobtest.simple;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.job.MavenJobConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleRunner {

    private final int executorCount = 2;

    @Test
    public void testJobManager() {

        MavenJobConfig config = new MavenJobConfig("127.0.0.1", 0) {
            @Override
            public void startExecutors(String uniqueId, String serverUrl) {
                ExecutorService executor = Executors.newFixedThreadPool(executorCount);
                for (int i = 0; i < executorCount; i++) {
                    executor.submit(() -> {
                        JobExecutor.run(serverUrl);
                        return true;
                    });
                }
                executor.shutdown();
            }
        };
        config.addEnvPropKey("KARATE_TEST");
        Results results = Runner.path("classpath:jobtest/simple").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
