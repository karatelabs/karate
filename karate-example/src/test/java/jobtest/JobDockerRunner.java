package jobtest;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.MavenJobConfig;
import com.intuit.karate.shell.Command;
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
public class JobDockerRunner {

    @Test
    public void testJobManager() {
        
        MavenJobConfig config = new MavenJobConfig("host.docker.internal", 0) {           
            @Override
            public void startExecutors(String uniqueId, String serverUrl) {
                int count = 2;
                ExecutorService executor = Executors.newFixedThreadPool(count);
                List<Callable<Boolean>> list = new ArrayList();
                for (int i = 0; i < count; i++) {
                    list.add(() -> {
                        Command.execLine(null, "docker run karate-base -j " + serverUrl);
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
        Results results = Runner.path("classpath:jobtest").jobConfig(config).parallel(2);
    }

}
