package mock

import com.intuit.karate.Runner
import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class CatsSimulationWithSilentWarmUp extends Simulation {

  MockUtils.startServer(0)

  val protocol = karateProtocol(
    "/cats/{id}" -> Nil,
    "/cats" -> pauseFor("get" -> 15, "post" -> 25)
  )

  protocol.nameResolver = (req, ctx) => req.getHeader("karate-name")
  protocol.runner.karateEnv("perf")

  val createWarmup = scenario("create warm-up").exec(karateFeature("classpath:mock/cats-create.feature").silent)
  val create = scenario("create").exec(karateFeature("classpath:mock/cats-create.feature")).exec(session => {
    println("*** id in gatling: " + session("id").as[String])
    println("*** session status in gatling: " + session.status)
    session
  })

  val deleteWarmup = scenario("delete warm-up").exec(karateFeature("classpath:mock/cats-delete.feature").silent)
  val delete = scenario("delete").group("delete cats") {
    exec(karateFeature("classpath:mock/cats-delete.feature@name=delete"))
  }
  
  val customWarmup = scenario("custom warm-up").exec(karateFeature("classpath:mock/cats-rpc.feature").silent)
  val custom = scenario("custom").exec(karateFeature("classpath:mock/custom-rpc.feature"))

  setUp(
    createWarmup.inject(rampUsers(1) during (5 seconds)).andThen(
      create.inject(rampUsers(10) during (5 seconds))
    ),
    deleteWarmup.inject(rampUsers(1) during (5 seconds)).andThen(
      delete.inject(rampUsers(5) during (5 seconds))
    ),
    customWarmup.inject(rampUsers(1) during (5 seconds)).andThen(
      custom.inject(rampUsers(10) during (5 seconds))
    )
  ).protocols(protocol)

}
