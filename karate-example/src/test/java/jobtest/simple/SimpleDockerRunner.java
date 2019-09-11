package jobtest.simple;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobCommand;
import com.intuit.karate.job.MavenJobConfig;
import com.intuit.karate.shell.Command;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleDockerRunner {

    private final int executorCount = 2;

    @Test
    public void testJobManager() {

        MavenJobConfig config = new MavenJobConfig("host.docker.internal", 0) {
            @Override
            public void startExecutors(String jobId, String jobUrl) {
                ExecutorService executor = Executors.newFixedThreadPool(executorCount);
                for (int i = 0; i < executorCount; i++) {
                    executor.submit(() -> {
                        Command.execLine(null, "docker run --cap-add=SYS_ADMIN -e KARATE_JOBURL=" + jobUrl 
                                + " docker.intuit.com/sandbox/sandbox/pthomas3-test2/service/karate-chrome:latest");
                        return true;
                    });
                }
                executor.shutdown();
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
