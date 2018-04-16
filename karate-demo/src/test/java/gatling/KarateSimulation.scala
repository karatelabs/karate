package gatling

import com.intuit.karate.gatling.PreDef._
import demo.TestBase
import io.gatling.core.Predef._

class KarateSimulation extends Simulation {

  TestBase.startServer()

  val protocol = karateProtocol("/cats/{id}", "/dogs/{id}")

  val cats = scenario("cats").exec(karateFeature("classpath:demo/cats/cats.feature"))
  val dogs = scenario("dogs").exec(karateFeature("classpath:demo/dogs/dogs.feature"))

  setUp(
    cats.inject(atOnceUsers(10)).protocols(protocol),
    dogs.inject(atOnceUsers(5)).protocols(protocol)
  )

}
