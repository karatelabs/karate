package jobtest;

import com.intuit.karate.gatling.GatlingJobServer;
import com.intuit.karate.gatling.GatlingMavenJobConfig;

/**
 *
 * @author pthomas3
 */
public class GatlingDockerJobRunner {

    public static void main(String[] args) {
        GatlingMavenJobConfig config = new GatlingMavenJobConfig(2, "host.docker.internal", 0);
        GatlingJobServer server = new GatlingJobServer(config);
        server.startExecutors();
        server.waitSync();
        io.gatling.app.Gatling.main(new String[]{"-ro", "reports", "-rf", "target"});
    }

}
