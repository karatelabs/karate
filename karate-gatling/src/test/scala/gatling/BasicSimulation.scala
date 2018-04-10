package gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._

//class BasicSimulation extends Simulation {
//
//  val httpConf = http.baseURL("http://computer-database.gatling.io")
//
//  val scn = scenario("Scenario Name")
//    .exec(http("request_1").get("/"))
//    .exec(http("request_2").get("/computers?f=macbook"))
//    .exec(http("request_3").get("/computers/6"))
//    .exec(http("request_4").get("/"))
//    .exec(http("request_5").get("/computers?p=1"))
//    .exec(http("request_6").get("/computers?p=2"))
//    .exec(http("request_7").get("/computers?p=3"))
//    .exec(http("request_8").get("/computers?p=4"))
//    .exec(http("request_9").get("/computers/new"))
//    .exec(http("request_10")
//      .post("/computers")
//      .formParam("name", "Beautiful Computer")
//      .formParam("introduced", "2012-05-30")
//      .formParam("discontinued", "")
//      .formParam("company", "37"))
//
//  setUp(scn.inject(atOnceUsers(1)).protocols(httpConf))
//
//}
