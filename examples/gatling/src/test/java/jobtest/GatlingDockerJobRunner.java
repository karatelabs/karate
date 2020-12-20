package jobtest;

import com.intuit.karate.gatling.GatlingMavenJobConfig;
import com.intuit.karate.job.JobManager;
import java.io.File;

/**
 *
 * @author pthomas3
 */
public class GatlingDockerJobRunner {

    public static void main(String[] args) {
        // note that on a mac docker (desktop) needs around 4 GB memory to be allocated for 2 parallel instances
        String mavenHome = System.getProperty("user.home") + File.separator + ".m2";
        GatlingMavenJobConfig config = new GatlingMavenJobConfig(2, "host.docker.internal", 0);
        config.setAddOptions("-v " + mavenHome + ":/root/.m2");
        JobManager manager = new JobManager(config);
        manager.start();
        manager.waitForCompletion();
    }

}
