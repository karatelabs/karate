package jobtest.web;

import common.ReportUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.JobContext;
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
public class WebDockerRunner {

    @Test
    public void testJobManager() {
        
        int width = 1366;
        int height = 768;
        int executorCount = 2;
        
        MavenJobConfig config = new MavenJobConfig("host.docker.internal", 0) {
            @Override
            public void startExecutors(String jobId, String jobUrl) {
                ExecutorService executor = Executors.newFixedThreadPool(executorCount);
                for (int i = 0; i < executorCount; i++) {
                    executor.submit(() -> {
                        Command.execLine(null, "docker run --cap-add=SYS_ADMIN -e KARATE_JOBURL=" + jobUrl
                                + " -e KARATE_WIDTH=" + width + " -e KARATE_HEIGHT=" + height + " karate-chrome");
                    });
                }
                executor.shutdown();
            }

            @Override
            public List<JobCommand> getPreCommands(JobContext jc) {
                String command = "ffmpeg -f x11grab -r 16 -s " + width + "x" + height
                        + " -i :1 -vcodec libx264 -pix_fmt yuv420p -preset fast "
                        + jc.getUploadDir() + "/" + jc.getChunkId() + ".mp4";
                return Collections.singletonList(new JobCommand(command, null, true)); // background true
            }

            @Override
            public List<JobCommand> getShutdownCommands() {
                return Collections.singletonList(new JobCommand("supervisorctl shutdown"));
            }

        };
        Results results = Runner.path("classpath:jobtest/web").startServerAndWait(config);
        ReportUtils.generateReport(results.getReportDir());
    }

}
