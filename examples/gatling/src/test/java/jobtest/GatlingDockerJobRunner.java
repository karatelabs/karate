package jobtest;

import com.intuit.karate.job.GatlingMavenJobConfig;
import com.intuit.karate.job.JobManager;

/**
 *
 * @author pthomas3
 */
public class GatlingDockerJobRunner {

    public static void main(String[] args) {
        GatlingMavenJobConfig config = new GatlingMavenJobConfig(2, "host.docker.internal", 0);
        JobManager manager = new JobManager(config);
        manager.startExecutors();
        manager.waitForCompletion();
        io.gatling.app.Gatling.main(new String[]{"-ro", "reports", "-rf", "target"});
    }

}
