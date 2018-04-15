package mock

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

class CatsSimulation extends Simulation {

  MockUtils.startServer()

  val cats = scenario("cats").exec(karateFeature("classpath:mock/cats.feature"))

  setUp(
    cats.inject(atOnceUsers(10)).protocols(karateProtocol)
  )

}
