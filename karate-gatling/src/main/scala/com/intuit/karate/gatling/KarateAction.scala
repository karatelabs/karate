package com.intuit.karate.gatling

import io.gatling.commons.stats.OK
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateAction(protocol: KarateProtocol, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  override def name = "karate_action"

  override def execute(session: Session) = {
    val start = System.currentTimeMillis
    Thread.sleep(200)
    val end = System.currentTimeMillis
    val timings = ResponseTimings(start, end)
    statsEngine.logResponse(session, name, timings, OK, None, None)
    next ! session
  }

}
