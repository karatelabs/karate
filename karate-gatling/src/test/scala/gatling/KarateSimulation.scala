package gatling

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

class KarateSimulation extends Simulation {

  val scn = scenario("test").exec(feature("foo"))

  setUp(scn.inject(atOnceUsers(1)).protocols(karateProtocol))

}
