package mock;

import com.intuit.karate.gatling.javaapi.KarateProtocolBuilder;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static com.intuit.karate.gatling.javaapi.KarateDsl.*;

public class CatsSimulation extends Simulation {

  public CatsSimulation() {
      
    MockUtils.startServer(0);

    KarateProtocolBuilder protocol = karateProtocol(
      uri("/cats/{id}").nil(),
      uri("/cats").pauseFor(method("get", 15), method("post", 25)
    ));

    protocol.nameResolver = (req, ctx) -> req.getHeader("karate-name");
    protocol.runner.karateEnv("perf");

    ScenarioBuilder create = scenario("create").exec(karateFeature("classpath:mock/cats-create.feature")).exec(session -> {
      System.out.println("*** id in gatling: " + session.getString("id"));
      System.out.println("*** session status in gatling: " + session.asScala().status());
      return session;
    });

    ScenarioBuilder delete = scenario("delete").group("delete cats").on(
      exec(karateFeature("classpath:mock/cats-delete.feature@name=delete"))
    );

    ScenarioBuilder custom = scenario("custom").exec(karateFeature("classpath:mock/custom-rpc.feature"));

    setUp(
      create.injectOpen(rampUsers(10).during(5)).protocols(protocol),
      delete.injectOpen(rampUsers(5).during(5)).protocols(protocol),
      custom.injectOpen(rampUsers(10).during(5)).protocols(protocol)
    );
  }
}
