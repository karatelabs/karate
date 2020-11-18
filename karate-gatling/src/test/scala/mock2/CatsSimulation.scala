package mock2

import java.util.concurrent.ConcurrentLinkedDeque

import com.intuit.karate.gatling.PreDef._
import io.gatling.core.Predef._

import scala.concurrent.duration._

class CatsSimulation extends Simulation {

  MockUtils.startServer()

  val protocol = karateProtocol(
    "/cats/{id}" -> Nil,
    "/cats" ->  Nil
  )

  val nameFeeder = Array(
    Map("name" -> "Amy"),
    Map("name" -> "Billie"),
    Map("name" -> "Charlie"),
    Map("name" -> "Dot"),
    Map("name" -> "Eve")
  )
  val count = nameFeeder.readRecords.length
  val idQueue = new ConcurrentLinkedDeque[String]

  val create = scenario("create")
    .feed(nameFeeder)
    .exec(karateFeature("classpath:mock2/cats-create.feature"))
    .exec { session =>
      // If the above karate feature executed successfully then there will be an "id" attribute
      // Save this to the queue
      if (session.contains("id")) {
        val id = session("id").as[String]
        idQueue.add(id)
      }
      session
    }
  val delete = scenario("delete")
    .exec { session =>
      // Read an id from a queue and feed it to the session
      val id = idQueue.poll()
      if (id != null) {
        session.set("id", id)
      } else {
        session
      }
    }
    // Don't continue to execute the following karate feature if the session doesn't have an id
    .exitHereIf { session => !session.contains("id") }
    .exec(karateFeature("classpath:mock2/cats-delete.feature"))

  setUp(
    create.inject(rampUsers(count) during (5 seconds)).protocols(protocol).andThen(
      delete.inject(rampUsers(count) during (5 seconds)).protocols(protocol)
    )
  )
}
