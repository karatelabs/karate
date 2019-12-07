package jobtest;

import com.intuit.karate.gatling.GatlingJobServer;
import com.intuit.karate.gatling.GatlingMavenJobConfig;
import com.intuit.karate.job.JobExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pthomas3
 */
public class GatlingRunner {

    public static void main(String[] args) {
        GatlingMavenJobConfig config = new GatlingMavenJobConfig(-1, "127.0.0.1", 0) {
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
        GatlingJobServer server = new GatlingJobServer(config);
        server.startExecutors();
        server.waitSync();
        io.gatling.app.Gatling.main(new String[]{"-ro", "reports", "-rf", "target"});
    }

}
