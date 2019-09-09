package jobtest.simple;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.job.MavenJobConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleRunner {

    @Test
    public void testJobManager() {
        
        MavenJobConfig config = new MavenJobConfig("127.0.0.1", 0) {           
            @Override
            public void startExecutors(String uniqueId, String serverUrl) {
                int count = 2;
                ExecutorService executor = Executors.newFixedThreadPool(count);
                List<Callable<Boolean>> list = new ArrayList();
                for (int i = 0; i < count; i++) {
                    list.add(() -> {
                        JobExecutor.run(serverUrl);
                        return true;
                    });
                }
                try {
                    List<Future<Boolean>> futures = executor.invokeAll(list);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                executor.shutdownNow();
            }
        };
        config.addEnvPropKey("KARATE_TEST");
        Results results = Runner.path("classpath:jobtest/simple").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
