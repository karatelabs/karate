package perf

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class TestSimulation extends Simulation {

  val protocol = karateProtocol()
  TestUtils.startServer()

  val test = scenario("test").exec(karateFeature("classpath:perf/test.feature"))

  setUp(
    test.inject(rampUsers(10) during (5 seconds)).protocols(protocol)
  )

}
