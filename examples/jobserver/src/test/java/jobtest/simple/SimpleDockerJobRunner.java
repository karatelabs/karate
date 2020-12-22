package jobtest.simple;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.job.MavenJobConfig;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class SimpleDockerJobRunner {

    @Test
    void testJobManager() {
        MavenJobConfig config = new MavenJobConfig(2, "host.docker.internal", 0);
        Results results = Runner.path("classpath:jobtest/simple").jobManager(config);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}
