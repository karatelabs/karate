package com.intuit.karate.gatling

import akka.actor.{ActorSystem, Props}
import com.intuit.karate.cucumber.StepResult
import com.intuit.karate._
import gherkin.formatter.model.Step
import io.gatling.commons.stats.OK
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings

class KarateAction(val name: String, protocol: KarateProtocol, val statsEngine: StatsEngine, val next: Action) extends ExitableAction {

  val file = FileUtils.getFeatureFile(name)

  override def execute(session: Session) = {

    val stepInterceptor = new StepInterceptor {
      var start: Long = 0
      override def before(s: String, i: Int, step: Step, ctx: ScriptContext): Unit = {
        if (step.getName.startsWith("method")) {
          start = System.currentTimeMillis
        } else {
          start = 0
        }
      }
      override def after(s: String, i: Int, stepResult: StepResult, ctx: ScriptContext): Unit = {
        if (start != 0) {
          val end = System.currentTimeMillis
          val timings = ResponseTimings(start, end)
          val request = ctx.getPrevRequest
          val key = request.getMethod + " " + request.getUri
          statsEngine.logResponse(session, key, timings, OK, None, None)
        }
      }
    }

    val cc = new CallContext(null, 0, null, -1, false, true, null, stepInterceptor)

    val system = ActorSystem("karate")
    val act = system.actorOf(Props[KarateActor], name = "karate-actor")
    act ! new KarateMessage(file, cc, session, next)

  }

}


