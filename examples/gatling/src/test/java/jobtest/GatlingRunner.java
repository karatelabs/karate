package jobtest;

import com.intuit.karate.gatling.GatlingMavenJobConfig;
import com.intuit.karate.job.JobExecutor;
import com.intuit.karate.job.JobManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pthomas3
 */
public class GatlingRunner {

    public static void main(String[] args) {
        int executorCount = 2;
        GatlingMavenJobConfig config = new GatlingMavenJobConfig(executorCount, "127.0.0.1", 0) {
            @Override
            public void onStart(String uniqueId, String serverUrl) {
                ExecutorService executor = Executors.newFixedThreadPool(executorCount);
                for (int i = 0; i < executorCount; i++) {
                    executor.submit(() -> JobExecutor.run(serverUrl));
                }
                executor.shutdown();
                try {
                    executor.awaitTermination(0, TimeUnit.MINUTES);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        JobManager<Integer> manager = new JobManager(config);
        manager.start();
        manager.waitForCompletion();
    }

}
