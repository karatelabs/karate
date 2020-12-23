package jobtest.web;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
public class WebDockerRunner {

    @Test
    void test() {        
        // docker run --name karate --rm -p 5900:5900 --cap-add=SYS_ADMIN -v "$PWD":/src -v "$HOME/.m2":/root/.m2 ptrthomas/karate-chrome
        // open vnc://localhost:5900
        // docker exec -it -w /src karate mvn clean test -Dtest=jobtest.web.WebDockerRunner
        // docker exec -it -w /src karate bash
        // mvn clean test -Dtest=jobtest.web.WebDockerRunner
        System.setProperty("karate.env", "docker");
        Results results = Runner.path("classpath:jobtest/web").parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }

}
