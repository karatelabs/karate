package gatling

import com.intuit.karate.gatling.PreDef._
import demo.DemoUtils
import io.gatling.core.Predef._

class KarateSimulation extends Simulation {

  DemoUtils.copyFeatureFilesAndStartServer()

  val cats = scenario("cats").exec(karateFeature("classpath:demo/cats/cats.feature"))
  val dogs = scenario("dogs").exec(karateFeature("classpath:demo/dogs/dogs.feature"))

  setUp(
    cats.inject(atOnceUsers(10)).protocols(karateProtocol),
    dogs.inject(atOnceUsers(5)).protocols(karateProtocol)
  )

}
