package mock;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import static com.intuit.karate.gatling.javaapi.KarateDsl.*;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.*;

public class CatsChainedSimulation extends Simulation {

  public CatsChainedSimulation() {
    MockUtils.startServer(0);

    ProtocolBuilder protocol = karateProtocol(uri("/cats/{id}").nil());
  
    Iterator<Map<String, Object>> feeder = Stream.generate(() -> Collections.<String, Object>singletonMap("name", new Random().nextInt(20)+"-name")).iterator();
  
    ScenarioBuilder createAndRead = scenario("createAndRead")
    .group("createAndRead").on(
      feed(() -> feeder)
        .exec(karateSet("name", session -> session.getString("name")))
        .exec(karateFeature("classpath:mock/cats-chained.feature@name=create"))
        // for demo: injecting a new variable name expected by the 'read' feature
        .exec(karateSet("expectedName", session -> session.getString("name")))
        .exec(karateFeature("classpath:mock/cats-chained.feature@name=read")).exec(session -> {
      System.out.println("*** id in gatling: " + session.getString("id"));
      System.out.println("*** session status in gatling: " + session.asScala().status());
      return session;
    }));
    
  
    setUp(
      createAndRead.injectOpen(rampUsers(10).during(5)).protocols(protocol)
    ).assertions(details("createAndRead").failedRequests().percent().is(0d));
  
  }

}
