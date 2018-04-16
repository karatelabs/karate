package mock

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._
import scala.concurrent.duration._

class CatsSimulation extends Simulation {

  MockUtils.startServer()

  val protocol = karateProtocol("/cats/{id}")
  val cats = scenario("cats").exec(karateFeature("classpath:mock/cats.feature"))

  setUp(
    cats.inject(rampUsers(10) over (2 seconds)).protocols(protocol)
  )

}
