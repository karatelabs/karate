package jobtest.simple;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobCommand;
import com.intuit.karate.job.MavenJobConfig;
import com.intuit.karate.shell.Command;
import java.util.ArrayList;
import java.util.Collections;
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
public class SimpleDockerRunner {

    @Test
    public void testJobManager() {
        
        MavenJobConfig config = new MavenJobConfig("host.docker.internal", 0) {           
            @Override
            public void startExecutors(String jobId, String jobUrl) {
                int count = 2;
                ExecutorService executor = Executors.newFixedThreadPool(count);
                List<Callable<Boolean>> list = new ArrayList();
                for (int i = 0; i < count; i++) {
                    list.add(() -> {
                        Command.execLine(null, "docker run --cap-add=SYS_ADMIN -e KARATE_JOBURL=" + jobUrl + " karate-chrome");
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

            @Override
            public List<JobCommand> getShutdownCommands() {
                return Collections.singletonList(new JobCommand("supervisorctl shutdown"));
            }
            
        };
        Results results = Runner.path("classpath:jobtest/simple").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
