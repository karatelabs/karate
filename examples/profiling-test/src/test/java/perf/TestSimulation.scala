package perf

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class TestSimulation extends Simulation {

  val protocol = karateProtocol()
  TestUtils.startServer()

  val main = scenario("main").exec(karateFeature("classpath:perf/main.feature"))
  val called = scenario("called").exec(karateFeature("classpath:perf/called.feature"))

  val chained = scenario("chained")
       .exec(main)
       .exec(karateSet("extraKey", s => "extraValue"))
       .exec(called)

  setUp(
    chained.inject(
      rampUsers(20).during(5.seconds),
      constantUsersPerSec(20).during(10.minutes)
    ).protocols(protocol)
  )

}
