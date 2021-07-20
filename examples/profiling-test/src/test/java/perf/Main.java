package perf;

import com.intuit.karate.PerfHook;
import com.intuit.karate.Runner;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.PerfEvent;
import com.intuit.karate.core.ScenarioRuntime;
import com.intuit.karate.http.HttpRequest;

/**
 *
 * @author pthomas3
 */
public class Main {

    public static void main(String[] args) {
        TestUtils.startServer();
        int count = 0;
        PerfHook hook = new PerfHook() {
            @Override
            public String getPerfEventName(HttpRequest request, ScenarioRuntime sr) {
                return request.getMethod() + " " + request.getUrl();
            }

            @Override
            public void reportPerfEvent(PerfEvent event) {

            }

            @Override
            public void submit(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void afterFeature(FeatureResult fr) {

            }

            @Override
            public void pause(Number millis) {

            }

        };
        while (true) {
            count++;
            Runner.callAsync(Runner.builder(), "classpath:perf/test.feature", null, hook);
            System.out.print(count + " ");
            if (count % 100 == 0) {
                System.out.println("");
            }            
        }
    }

}
