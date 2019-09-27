package mock

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class CatsGatlingSimulation extends Simulation {

  MockUtils.startServer()

  val httpConf = http.baseUrl(System.getProperty("mock.cats.url"))

  val create = scenario("create")
    .pause(25 milliseconds)
    .exec(http("POST /cats")
      .post("/")
      .body(StringBody("""{ "name": "Billie" }"""))
      .check(status.is(200))
      .check(jsonPath("$.name").is("Billie"))
      .check(jsonPath("$.id")
        .saveAs("id")))

    .pause(10 milliseconds).exec(
    http("GET /cats/{id}")
      .get("/${id}")
      .check(status.is(200))
      .check(jsonPath("$.id").is("${id}"))
      // intentional assertion failure
      .check(jsonPath("$.name").is("Billi")))
    .exitHereIfFailed
    .exec(
      http("PUT /cats/{id}")
        .put("/${id}")
        .body(StringBody("""{ "id":"${id}", "name": "Bob" }"""))
        .check(status.is(200))
        .check(jsonPath("$.id").is("${id}"))
        .check(jsonPath("$.name").is("Bob")))

    .pause(10 milliseconds).exec(
    http("GET /cats/{id}")
      .get("/${id}")
      .check(status.is(200)))

  val delete = scenario("delete")
    .pause(15 milliseconds).exec(
    http("GET /cats")
      .get("/")
      .check(status.is(200))
      .check(jsonPath("$[*].id").findAll.optional
        .saveAs("ids")))

    .doIf(_.contains("ids")) {
      foreach("${ids}", "id") {
        pause(20 milliseconds).exec(
          http("DELETE /cats/{id}")
            .delete("/${id}")
            .check(status.is(200))
            .check(bodyString.is("")))

          .pause(10 milliseconds).exec(
          http("GET /cats/{id}")
            .get("/${id}")
            .check(status.is(404)))
      }
    }

  setUp(
    create.inject(rampUsers(10) during (5 seconds)).protocols(httpConf),
    delete.inject(rampUsers(5) during (5 seconds)).protocols(httpConf)
  )

}
