package mock

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class CatsCreateReadSimulation extends Simulation {

  MockUtils.startServer(0)

  val protocol = karateProtocol(
    "/cats/{id}" -> Nil,
    "/cats" -> pauseFor("get" -> 15, "post" -> 25)
  )

  protocol.nameResolver = (req, ctx) => req.getHeader("karate-name")

  val createOnly = scenario("create").exec(karateFeature("classpath:mock/cats-cr.feature@name=create")).exec(session => {
    println("*** session status in gatling: " + session.status)
    println("*** session in gatling create: " + session)
    session
  })

  val readOnly = scenario("read").exec(karateFeature("classpath:mock/cats-cr.feature@name=read")).exec(session => {
    println("*** id in gatling: " + session("id").as[String])
    println("*** session status in gatling: " + session.status)
    println("*** session in gatling during read: " + session)
    session
  })

  val createAndRead = scenario("createAndRead").group("createAndRead") {
    exec(createOnly).exec(readOnly)
  }


  setUp(
    createAndRead.inject(rampUsers(10) during (5 seconds)).protocols(protocol)
  ).assertions(details("createAndRead").failedRequests.percent.is(0))

}
